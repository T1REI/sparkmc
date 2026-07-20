use std::collections::HashSet;
use std::io::{BufRead, BufReader, Read, Write};
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::sync::mpsc::{Receiver, Sender, channel};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use crate::java;
use crate::plan::LaunchPlan;
use crate::term::{self, ConsoleIo, KeyEvent, RawMode};

const RESET: &str = "\x1b[0m";
const WHITE: &str = "\x1b[38;2;216;222;233m";
const GRAY: &str = "\x1b[38;2;118;124;138m";
const GREEN: &str = "\x1b[38;2;138;214;140m";
const RED: &str = "\x1b[38;2;232;118;110m";
const YELLOW: &str = "\x1b[38;2;232;192;102m";
const CYAN: &str = "\x1b[38;2;122;190;238m";

type Flag = Arc<Mutex<Option<u32>>>;

enum InMsg {
    Line(String),
    Quit,
}

pub fn run(dir: &Path) {
    let (io, stdin_reader) = open_console();
    let raw = RawMode::enable().ok();
    let (tx, rx) = channel::<InMsg>();
    if raw.is_some() {
        spawn_raw_input_loop(io.clone(), stdin_reader, tx);
    } else {
        spawn_line_input_loop(io.clone(), stdin_reader, tx);
    }
    let _raw = raw;

    let mut plan = match LaunchPlan::load(dir) {
        Ok(plan) => plan,
        Err(e) => {
            system(&io, &format!("{RED}sparkmc: {e}{RESET}"));
            wait_enter(&io, &rx);
            return;
        }
    };
    io.print_raw(&format!("\x1b]0;{}\x07", plan.title()));

    let mut program = plan.program();
    let mut tried: HashSet<u32> = HashSet::new();

    if let Some(major) = plan.required_java {
        match ensure_java(major, &program, &io, &rx, &mut plan, dir, &mut tried) {
            Some(path) => program = path,
            None => return,
        }
    } else if java::installed_major(&program).is_none() {
        if let Some(path) = java::resolve(21, plan.java.as_deref())
            .or_else(|| java::resolve(17, plan.java.as_deref()))
            .or_else(|| java::resolve(8, plan.java.as_deref()))
        {
            program = path.to_string_lossy().into_owned();
            plan.java = Some(program.clone());
            let _ = plan.save(dir);
            system(&io, &format!("using Java at {program}"));
        }
    }

    loop {
        let args = plan.args();
        system(&io, &format!("{} {}", program, args.join(" ")));
        let needed: Flag = Arc::new(Mutex::new(None));
        let mut child = match spawn(dir, &program, &args) {
            Ok(child) => child,
            Err(e) => {
                system(&io, &format!("failed to start java: {e}"));
                wait_enter(&io, &rx);
                return;
            }
        };

        let mut pumps = Vec::new();
        if let Some(pipe) = child.stdout.take() {
            pumps.push(pump(pipe, io.clone(), false, needed.clone()));
        }
        if let Some(pipe) = child.stderr.take() {
            pumps.push(pump(pipe, io.clone(), true, needed.clone()));
        }
        let mut stdin_pipe = child.stdin.take();
        system(&io, "server started");
        io.set_prompt_enabled(true);

        loop {
            while let Ok(msg) = rx.try_recv() {
                match msg {
                    InMsg::Quit => {
                        let _ = child.kill();
                        io.set_prompt_enabled(false);
                        return;
                    }
                    InMsg::Line(cmd) => {
                        if let Some(pipe) = stdin_pipe.as_mut() {
                            let _ = writeln!(pipe, "{cmd}");
                            let _ = pipe.flush();
                        }
                    }
                }
            }
            if let Ok(Some(status)) = child.try_wait() {
                let code = status
                    .code()
                    .map(|c| c.to_string())
                    .unwrap_or_else(|| "terminated".into());
                io.set_prompt_enabled(false);
                system(&io, &format!("process exited ({code})"));
                break;
            }
            thread::sleep(Duration::from_millis(40));
        }

        for handle in pumps {
            let _ = handle.join();
        }

        let required = *needed.lock().unwrap();
        if let Some(major) = required {
            match ensure_java(major, &program, &io, &rx, &mut plan, dir, &mut tried) {
                Some(path) => {
                    program = path;
                    continue;
                }
                None => return,
            }
        }

        if !plan.auto_restart {
            system(&io, "server stopped, press Enter to close");
            wait_enter(&io, &rx);
            return;
        }
        system(&io, "restarting in 5 seconds... close this window to cancel");
        thread::sleep(Duration::from_secs(5));
    }
}

fn spawn_raw_input_loop(io: Arc<ConsoleIo>, mut reader: Box<dyn Read + Send>, tx: Sender<InMsg>) {
    thread::spawn(move || {
        loop {
            match term::read_key(&mut reader) {
                Ok(KeyEvent::Eof) | Ok(KeyEvent::CtrlC) | Err(_) => {
                    let _ = tx.send(InMsg::Quit);
                    break;
                }
                Ok(KeyEvent::Enter) => {
                    let line = io.submit();
                    if tx.send(InMsg::Line(line)).is_err() {
                        break;
                    }
                }
                Ok(KeyEvent::Backspace) => io.backspace(),
                Ok(KeyEvent::Tab) => io.tab_complete(),
                Ok(KeyEvent::Char(c)) => io.push_char(c),
                Ok(KeyEvent::Other) => {}
            }
        }
    });
}

fn spawn_line_input_loop(io: Arc<ConsoleIo>, reader: Box<dyn Read + Send>, tx: Sender<InMsg>) {
    thread::spawn(move || {
        let mut buffered = BufReader::new(reader);
        let mut text = String::new();
        loop {
            io.set_prompt_enabled(true);
            text.clear();
            match buffered.read_line(&mut text) {
                Ok(0) => {
                    let _ = tx.send(InMsg::Quit);
                    break;
                }
                Ok(_) => {
                    let line = text.trim_end_matches(['\r', '\n']).to_string();
                    if tx.send(InMsg::Line(line)).is_err() {
                        break;
                    }
                }
                Err(_) => {
                    let _ = tx.send(InMsg::Quit);
                    break;
                }
            }
        }
    });
}

fn ensure_java(
    major: u32,
    current: &str,
    io: &ConsoleIo,
    inputs: &Receiver<InMsg>,
    plan: &mut LaunchPlan,
    dir: &Path,
    tried: &mut HashSet<u32>,
) -> Option<String> {
    if java::installed_major(current) == Some(major) {
        return Some(current.to_string());
    }

    if let Some(path) = java::resolve(major, plan.java.as_deref()) {
        let program = path.to_string_lossy().into_owned();
        if program != current {
            system(io, &format!("found Java {major}: {program}"));
        }
        plan.java = Some(program.clone());
        let _ = plan.save(dir);
        return Some(program);
    }

    obtain_java(major, io, inputs, plan, dir, tried)
}

fn obtain_java(
    major: u32,
    io: &ConsoleIo,
    inputs: &Receiver<InMsg>,
    plan: &mut LaunchPlan,
    dir: &Path,
    tried: &mut HashSet<u32>,
) -> Option<String> {
    if tried.contains(&major) {
        system(io, &format!("still failing with Java {major}, aborting"));
        wait_enter(io, inputs);
        return None;
    }

    system(io, &format!("Java {major} not found on this system"));
    loop {
        ask(io, &format!("This server needs Java {major}. Choose:"));
        ask(io, "  1. Download & install automatically");
        ask(io, "  2. Custom path (folder or java/javaw executable)");
        ask(io, "  3. Cancel");
        io.set_prompt_enabled(true);
        let choice = match read_answer(inputs) {
            Some(c) => c,
            None => {
                io.set_prompt_enabled(false);
                return None;
            }
        };
        io.set_prompt_enabled(false);
        match choice.trim() {
            "" | "1" => return install_java(major, io, inputs, plan, dir, tried),
            "2" => {
                if let Some(program) = custom_java(major, io, inputs, plan, dir) {
                    return Some(program);
                }
            }
            "3" | "n" | "no" => {
                system(
                    io,
                    "cannot start without the right Java. Press Enter to close",
                );
                wait_enter(io, inputs);
                return None;
            }
            other => system(io, &format!("unknown choice '{other}', enter 1, 2 or 3")),
        }
    }
}

fn install_java(
    major: u32,
    io: &ConsoleIo,
    inputs: &Receiver<InMsg>,
    plan: &mut LaunchPlan,
    dir: &Path,
    tried: &mut HashSet<u32>,
) -> Option<String> {
    tried.insert(major);
    let logger = |m: &str| system(io, m);
    match java::install(major, &logger) {
        Ok(path) => {
            let program = path.to_string_lossy().into_owned();
            plan.java = Some(program.clone());
            let _ = plan.save(dir);
            system(io, "Java ready — restarting server with new runtime");
            Some(program)
        }
        Err(e) => {
            system(io, &format!("Java install failed: {e}"));
            wait_enter(io, inputs);
            None
        }
    }
}

fn custom_java(
    major: u32,
    io: &ConsoleIo,
    inputs: &Receiver<InMsg>,
    plan: &mut LaunchPlan,
    dir: &Path,
) -> Option<String> {
    loop {
        ask(
            io,
            &format!("Path to Java {major} (javaw.exe, java.exe or its folder), empty to go back:"),
        );
        io.set_prompt_enabled(true);
        let answer = read_answer(inputs)?;
        io.set_prompt_enabled(false);
        if answer.trim().is_empty() {
            return None;
        }
        match java::from_custom_path(&answer, major) {
            Ok(path) => {
                let program = path.to_string_lossy().into_owned();
                plan.java = Some(program.clone());
                let _ = plan.save(dir);
                system(io, &format!("using Java {major}: {program}"));
                return Some(program);
            }
            Err(e) => system(io, &format!("{RED}{e}{RESET}")),
        }
    }
}

fn read_answer(inputs: &Receiver<InMsg>) -> Option<String> {
    match inputs.recv() {
        Ok(InMsg::Line(answer)) => Some(answer),
        Ok(InMsg::Quit) | Err(_) => None,
    }
}

fn spawn(dir: &Path, program: &str, args: &[String]) -> std::io::Result<Child> {
    Command::new(program)
        .args(args)
        .current_dir(dir)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
}

fn pump<R: Read + Send + 'static>(
    reader: R,
    io: Arc<ConsoleIo>,
    is_stderr: bool,
    needed: Flag,
) -> thread::JoinHandle<()> {
    thread::spawn(move || {
        let mut buffered = BufReader::new(reader);
        let mut bytes = Vec::new();
        loop {
            bytes.clear();
            match buffered.read_until(b'\n', &mut bytes) {
                Ok(0) => break,
                Ok(_) => {
                    let text = String::from_utf8_lossy(&bytes);
                    let text = text.trim_end_matches(['\r', '\n']);
                    if let Some(major) = java::needed_major_from_line(text) {
                        *needed.lock().unwrap() = Some(major);
                    }
                    io.print_line(&format!("{}{text}{RESET}", color_for(text, is_stderr)));
                }
                Err(_) => break,
            }
        }
    })
}

fn wait_enter(io: &ConsoleIo, inputs: &Receiver<InMsg>) {
    io.set_prompt_enabled(true);
    let _ = inputs.recv();
    io.set_prompt_enabled(false);
}

fn system(io: &ConsoleIo, msg: &str) {
    io.print_line(&format!("{GREEN}[sparkmc]{RESET} {WHITE}{msg}{RESET}"));
}

fn ask(io: &ConsoleIo, msg: &str) {
    io.print_line(&format!("{YELLOW}[sparkmc] {msg}{RESET}"));
}

fn color_for(line: &str, is_stderr: bool) -> &'static str {
    let upper = line.to_ascii_uppercase();
    if contains_any(
        &upper,
        &[
            "FATAL",
            "SEVERE",
            "/ERROR",
            "ERROR]",
            " ERROR",
            "EXCEPTION",
            "CAUSED BY",
            "\tAT ",
        ],
    ) {
        RED
    } else if contains_any(&upper, &["/WARN", "WARN]", " WARN", "WARNING"]) {
        YELLOW
    } else if contains_any(&upper, &["/DEBUG", "DEBUG]", "/TRACE", "TRACE]"]) {
        GRAY
    } else if contains_any(&upper, &["DONE (", "]: DONE", "FOR HELP, TYPE"]) {
        GREEN
    } else if contains_any(&upper, &["STARTING", "PREPARING", "LOADING", "RELOADING"]) {
        CYAN
    } else if is_stderr {
        YELLOW
    } else {
        WHITE
    }
}

fn contains_any(haystack: &str, needles: &[&str]) -> bool {
    needles.iter().any(|n| haystack.contains(n))
}

#[cfg(windows)]
fn open_console() -> (Arc<ConsoleIo>, Box<dyn Read + Send>) {
    use std::fs::OpenOptions;
    use std::os::raw::c_void;
    use std::os::windows::io::AsRawHandle;

    const ENABLE_VT: u32 = 0x0004;
    unsafe extern "system" {
        fn GetConsoleWindow() -> *mut c_void;
        fn AllocConsole() -> i32;
        fn GetConsoleMode(handle: *mut c_void, mode: *mut u32) -> i32;
        fn SetConsoleMode(handle: *mut c_void, mode: u32) -> i32;
    }
    unsafe {
        if GetConsoleWindow().is_null() {
            AllocConsole();
        }
    }

    let output = OpenOptions::new()
        .read(true)
        .write(true)
        .open("CONOUT$")
        .expect("open CONOUT$");
    unsafe {
        let handle = output.as_raw_handle() as *mut c_void;
        let mut mode = 0u32;
        if GetConsoleMode(handle, &mut mode) != 0 {
            SetConsoleMode(handle, mode | ENABLE_VT);
        }
    }
    let input = OpenOptions::new()
        .read(true)
        .write(true)
        .open("CONIN$")
        .expect("open CONIN$");

    let writer: Box<dyn Write + Send> = Box::new(output);
    let reader: Box<dyn Read + Send> = Box::new(input);
    (ConsoleIo::new(writer), reader)
}

#[cfg(not(windows))]
fn open_console() -> (Arc<ConsoleIo>, Box<dyn Read + Send>) {
    let writer: Box<dyn Write + Send> = Box::new(std::io::stdout());
    let reader: Box<dyn Read + Send> = Box::new(std::io::stdin());
    (ConsoleIo::new(writer), reader)
}
