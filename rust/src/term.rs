use std::io::{self, Read, Write};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use crate::commands;

const RESET: &str = "\x1b[0m";
const WHITE: &str = "\x1b[38;2;216;222;233m";
const GRAY: &str = "\x1b[38;2;118;124;138m";
const GREEN: &str = "\x1b[38;2;138;214;140m";

pub struct ConsoleIo {
    out: Mutex<Box<dyn Write + Send>>,
    buffer: Mutex<String>,
    prompt_on: AtomicBool,
}

impl ConsoleIo {
    pub fn new(writer: Box<dyn Write + Send>) -> Arc<Self> {
        Arc::new(Self {
            out: Mutex::new(writer),
            buffer: Mutex::new(String::new()),
            prompt_on: AtomicBool::new(false),
        })
    }

    pub fn set_prompt_enabled(&self, on: bool) {
        self.prompt_on.store(on, Ordering::SeqCst);
        if on {
            self.redraw_prompt();
        } else {
            self.clear_line();
        }
    }

    pub fn print_line(&self, text: &str) {
        if let Ok(mut out) = self.out.lock() {
            let _ = write!(out, "\r\x1b[2K{text}\n");
            let _ = out.flush();
        }
        if self.prompt_on.load(Ordering::SeqCst) {
            self.redraw_prompt();
        }
    }

    pub fn print_raw(&self, text: &str) {
        if let Ok(mut out) = self.out.lock() {
            let _ = out.write_all(text.as_bytes());
            let _ = out.flush();
        }
    }

    pub fn clear_line(&self) {
        if let Ok(mut out) = self.out.lock() {
            let _ = write!(out, "\r\x1b[2K");
            let _ = out.flush();
        }
    }

    pub fn redraw_prompt(&self) {
        let buffer = self
            .buffer
            .lock()
            .map(|b| b.clone())
            .unwrap_or_default();
        let suggestion = commands::suggest(&buffer);
        let ghost = suggestion
            .as_ref()
            .map(|s| s.ghost.as_str())
            .unwrap_or("");

        if let Ok(mut out) = self.out.lock() {
            let _ = write!(
                out,
                "\r\x1b[2K{GREEN}>{RESET} {WHITE}{buffer}{GRAY}{ghost}{RESET}"
            );
            if !ghost.is_empty() {
                let back = ghost.chars().count();
                let _ = write!(out, "\x1b[{back}D");
            }
            let _ = out.flush();
        }
    }

    pub fn push_char(&self, c: char) {
        if let Ok(mut buf) = self.buffer.lock() {
            buf.push(c);
        }
        self.redraw_prompt();
    }

    pub fn backspace(&self) {
        if let Ok(mut buf) = self.buffer.lock() {
            buf.pop();
        }
        self.redraw_prompt();
    }

    pub fn tab_complete(&self) {
        if let Ok(mut buf) = self.buffer.lock() {
            if let Some(s) = commands::suggest(&buf) {
                if !s.accept.is_empty() {
                    buf.push_str(&s.accept);
                }
            }
        }
        self.redraw_prompt();
    }

    pub fn submit(&self) -> String {
        let line = {
            let mut buf = self.buffer.lock().unwrap_or_else(|e| e.into_inner());
            let line = buf.clone();
            buf.clear();
            line
        };
        if let Ok(mut out) = self.out.lock() {
            let _ = write!(out, "\r\x1b[2K{GREEN}>{RESET} {WHITE}{line}{RESET}\n");
            let _ = out.flush();
        }
        if self.prompt_on.load(Ordering::SeqCst) {
            self.redraw_prompt();
        }
        line
    }
}

pub enum KeyEvent {
    Char(char),
    Enter,
    Backspace,
    Tab,
    CtrlC,
    Eof,
    Other,
}

pub fn read_key(reader: &mut dyn Read) -> io::Result<KeyEvent> {
    let mut buf = [0u8; 1];
    let n = reader.read(&mut buf)?;
    if n == 0 {
        return Ok(KeyEvent::Eof);
    }
    match buf[0] {
        b'\n' | b'\r' => Ok(KeyEvent::Enter),
        0x7f | 0x08 => Ok(KeyEvent::Backspace),
        b'\t' => Ok(KeyEvent::Tab),
        0x03 => Ok(KeyEvent::CtrlC),
        b'\x1b' => {
            let mut next = [0u8; 1];
            if reader.read(&mut next)? == 0 {
                return Ok(KeyEvent::Other);
            }
            if next[0] == b'[' {
                let mut code = [0u8; 1];
                let _ = reader.read(&mut code);
            }
            Ok(KeyEvent::Other)
        }
        c if (32..127).contains(&c) => Ok(KeyEvent::Char(c as char)),
        c if c >= 0xC0 => {
            let width = utf8_width(c);
            let mut bytes = vec![c];
            for _ in 1..width {
                let mut b = [0u8; 1];
                if reader.read(&mut b)? == 0 {
                    break;
                }
                bytes.push(b[0]);
            }
            Ok(String::from_utf8_lossy(&bytes)
                .chars()
                .next()
                .map(KeyEvent::Char)
                .unwrap_or(KeyEvent::Other))
        }
        _ => Ok(KeyEvent::Other),
    }
}

fn utf8_width(b: u8) -> usize {
    if b & 0xE0 == 0xC0 {
        2
    } else if b & 0xF0 == 0xE0 {
        3
    } else if b & 0xF8 == 0xF0 {
        4
    } else {
        1
    }
}

pub struct RawMode {
    #[cfg(windows)]
    in_mode: u32,
    #[cfg(windows)]
    out_mode: u32,
    #[cfg(not(windows))]
    active: bool,
}

impl RawMode {
    pub fn enable() -> io::Result<Self> {
        #[cfg(windows)]
        {
            enable_windows()
        }
        #[cfg(not(windows))]
        {
            enable_unix()
        }
    }
}

impl Drop for RawMode {
    fn drop(&mut self) {
        #[cfg(windows)]
        {
            restore_windows(self.in_mode, self.out_mode);
        }
        #[cfg(not(windows))]
        {
            if self.active {
                let _ = std::process::Command::new("stty").args(["sane"]).status();
            }
        }
    }
}

#[cfg(windows)]
fn enable_windows() -> io::Result<RawMode> {
    const ENABLE_PROCESSED_INPUT: u32 = 0x0001;
    const ENABLE_LINE_INPUT: u32 = 0x0002;
    const ENABLE_ECHO_INPUT: u32 = 0x0004;
    const ENABLE_VIRTUAL_TERMINAL_INPUT: u32 = 0x0200;
    const ENABLE_PROCESSED_OUTPUT: u32 = 0x0001;
    const ENABLE_WRAP_AT_EOL_OUTPUT: u32 = 0x0002;
    const ENABLE_VIRTUAL_TERMINAL_PROCESSING: u32 = 0x0004;
    const STD_INPUT: i32 = -10;
    const STD_OUTPUT: i32 = -11;

    unsafe extern "system" {
        fn GetStdHandle(n: i32) -> *mut std::ffi::c_void;
        fn GetConsoleMode(h: *mut std::ffi::c_void, mode: *mut u32) -> i32;
        fn SetConsoleMode(h: *mut std::ffi::c_void, mode: u32) -> i32;
    }

    unsafe {
        let hin = GetStdHandle(STD_INPUT);
        let hout = GetStdHandle(STD_OUTPUT);
        if hin.is_null() || hout.is_null() {
            return Err(io::Error::last_os_error());
        }

        let mut in_mode = 0u32;
        let mut out_mode = 0u32;
        if GetConsoleMode(hin, &mut in_mode) == 0 || GetConsoleMode(hout, &mut out_mode) == 0 {
            return Err(io::Error::last_os_error());
        }

        let new_in = (in_mode & !(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT))
            | ENABLE_PROCESSED_INPUT
            | ENABLE_VIRTUAL_TERMINAL_INPUT;
        let new_out = out_mode
            | ENABLE_PROCESSED_OUTPUT
            | ENABLE_WRAP_AT_EOL_OUTPUT
            | ENABLE_VIRTUAL_TERMINAL_PROCESSING;

        if SetConsoleMode(hin, new_in) == 0 || SetConsoleMode(hout, new_out) == 0 {
            return Err(io::Error::last_os_error());
        }

        Ok(RawMode { in_mode, out_mode })
    }
}

#[cfg(windows)]
fn restore_windows(in_mode: u32, out_mode: u32) {
    const STD_INPUT: i32 = -10;
    const STD_OUTPUT: i32 = -11;
    unsafe extern "system" {
        fn GetStdHandle(n: i32) -> *mut std::ffi::c_void;
        fn SetConsoleMode(h: *mut std::ffi::c_void, mode: u32) -> i32;
    }
    unsafe {
        let hin = GetStdHandle(STD_INPUT);
        let hout = GetStdHandle(STD_OUTPUT);
        if !hin.is_null() {
            SetConsoleMode(hin, in_mode);
        }
        if !hout.is_null() {
            SetConsoleMode(hout, out_mode);
        }
    }
}

#[cfg(not(windows))]
fn enable_unix() -> io::Result<RawMode> {
    let status = std::process::Command::new("stty")
        .args(["-echo", "-icanon", "min", "1", "time", "0"])
        .status()
        .map_err(io::Error::other)?;
    if !status.success() {
        return Err(io::Error::new(io::ErrorKind::Other, "stty failed"));
    }
    Ok(RawMode { active: true })
}
