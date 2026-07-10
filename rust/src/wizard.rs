use std::io::{self, BufRead, Write};
use std::path::Path;

use crate::event::Reporter;
use crate::model::{Core, FlagPreset, LoaderChannel, ServerConfig};
use crate::plan::LaunchPlan;
use crate::{net, setup, util};

const RESET: &str = "\x1b[0m";
const WHITE: &str = "\x1b[38;2;216;222;233m";
const GRAY: &str = "\x1b[38;2;118;124;138m";
const GREEN: &str = "\x1b[38;2;138;214;140m";
const RED: &str = "\x1b[38;2;232;118;110m";
const YELLOW: &str = "\x1b[38;2;232;192;102m";
const CYAN: &str = "\x1b[38;2;122;190;238m";

pub fn run(dir: &Path) {
    banner();

    let core = match pick_core() {
        Some(c) => c,
        None => return,
    };

    let versions = match load_versions(core) {
        Ok(v) => v,
        Err(e) => {
            err(&e);
            wait_enter();
            return;
        }
    };
    if versions.is_empty() {
        err("no versions returned");
        wait_enter();
        return;
    }

    let version = match pick_version(&versions) {
        Some(v) => v,
        None => return,
    };

    let channel = if core.supports_channel() {
        match pick_channel() {
            Some(c) => Some(c),
            None => return,
        }
    } else {
        None
    };

    let preset = match pick_flags() {
        Some(p) => p,
        None => return,
    };

    let custom_flags = if matches!(preset, FlagPreset::Custom) {
        match read_line("Custom JVM flags") {
            Some(s) if !s.trim().is_empty() => s,
            _ => {
                err("custom flags cannot be empty");
                wait_enter();
                return;
            }
        }
    } else {
        String::new()
    };

    let ram_mb = match pick_ram() {
        Some(r) => r,
        None => return,
    };

    let no_gui = confirm("No GUI (--nogui)?", true);
    let auto_restart = confirm("Auto restart on crash?", true);

    let cfg = ServerConfig {
        core,
        version,
        channel,
        preset,
        custom_flags,
        ram_mb,
        no_gui,
        auto_restart,
    };

    println!();
    info("Review");
    row("core", cfg.core.label());
    row("version", &cfg.version);
    if let Some(ch) = cfg.channel {
        row("channel", ch.label());
    }
    row("flags", cfg.preset.label());
    row("ram", &format!("{ram_mb} MB"));
    row("nogui", yes_no(cfg.no_gui));
    row("restart", yes_no(cfg.auto_restart));
    println!();

    if !confirm("Launch setup now?", true) {
        info("Cancelled");
        return;
    }

    let rep = Reporter::new();
    let prepared = match setup::run(dir, &cfg, &rep) {
        Ok(p) => p,
        Err(e) => {
            err(&e);
            wait_enter();
            return;
        }
    };

    let plan = LaunchPlan::from_config(&cfg, &prepared, dir);
    if let Err(e) = plan.save(dir) {
        err(&e);
        wait_enter();
        return;
    }

    info("Configuration saved. Starting server console...");
    crate::console::run(dir);
}

fn banner() {
    println!("{GREEN}sparkmc{RESET} {GRAY}- Minecraft server setup{RESET}");
    println!("{GRAY}Console wizard. Ctrl+C to abort.{RESET}");
    println!();
}

fn pick_core() -> Option<Core> {
    let items = Core::all();
    let labels: Vec<&str> = items.iter().map(|c| c.label()).collect();
    pick_index("Select core", &labels, 0).map(|i| items[i])
}

fn prompt_prefix() {
    print!("{GREEN}>{RESET} ");
    let _ = io::stdout().flush();
}

fn pick_version(versions: &[String]) -> Option<String> {
    let default = versions.first().map(String::as_str).unwrap_or("");
    println!("{CYAN}Versions:{RESET} {WHITE}{}{RESET}", versions.join(", "));
    println!("{GRAY}Type the version itself, e.g. 1.12.2 (not a number).{RESET}");
    loop {
        print!("{YELLOW}[sparkmc]{RESET} {WHITE}version{RESET} {GRAY}[{default}]{RESET}\n");
        prompt_prefix();
        let mut line = String::new();
        if io::stdin().read_line(&mut line).is_err() {
            return None;
        }
        let input = line.trim();
        let chosen = if input.is_empty() {
            default.to_string()
        } else {
            input.to_string()
        };
        match resolve_version(versions, &chosen) {
            Some(v) => {
                info(&format!("selected {v}"));
                return Some(v);
            }
            None => {
                err(&format!(
                    "unknown version '{chosen}'. Example: 1.12.2"
                ));
            }
        }
    }
}

fn resolve_version(versions: &[String], input: &str) -> Option<String> {
    let needle = input.trim();
    if needle.is_empty() {
        return None;
    }
    if let Some(exact) = versions.iter().find(|v| v.as_str() == needle) {
        return Some(exact.clone());
    }
    let lower = needle.to_ascii_lowercase();
    let case_hits: Vec<&String> = versions
        .iter()
        .filter(|v| v.to_ascii_lowercase() == lower)
        .collect();
    if case_hits.len() == 1 {
        return Some(case_hits[0].clone());
    }
    let prefix_hits: Vec<&String> = versions
        .iter()
        .filter(|v| v.to_ascii_lowercase().starts_with(&lower))
        .collect();
    if prefix_hits.len() == 1 {
        return Some(prefix_hits[0].clone());
    }
    None
}

fn pick_channel() -> Option<LoaderChannel> {
    let items = LoaderChannel::all();
    let labels: Vec<&str> = items.iter().map(|c| c.label()).collect();
    pick_index("Forge/NeoForge channel", &labels, 0).map(|i| items[i])
}

fn pick_flags() -> Option<FlagPreset> {
    let items = FlagPreset::all();
    let labels: Vec<&str> = items.iter().map(|p| p.label()).collect();
    pick_index("JVM flags preset", &labels, 0).map(|i| items[i])
}

fn pick_ram() -> Option<u32> {
    loop {
        print!("{YELLOW}[sparkmc]{RESET} {WHITE}RAM in MB {GRAY}[4096]{RESET}\n");
        prompt_prefix();
        let mut line = String::new();
        if io::stdin().read_line(&mut line).is_err() {
            return None;
        }
        let trimmed = line.trim();
        let value = if trimmed.is_empty() {
            4096
        } else {
            match trimmed.parse::<u32>() {
                Ok(v) if (1024..=65536).contains(&v) => v,
                _ => {
                    err("enter a number between 1024 and 65536");
                    continue;
                }
            }
        };
        let heap = util::heap_mb(value);
        if heap < util::MIN_HEAP_MB {
            err(&format!(
                "heap would be {heap} MB (min {}), pick more RAM",
                util::MIN_HEAP_MB
            ));
            continue;
        }
        info(&format!("heap ≈ {heap} MB"));
        return Some(value);
    }
}

fn load_versions(core: Core) -> Result<Vec<String>, String> {
    info(&format!("Loading {} versions...", core.label()));
    net::provider(core).versions()
}

fn pick_index(title: &str, labels: &[&str], default: usize) -> Option<usize> {
    println!("{CYAN}{title}{RESET}");
    for (i, label) in labels.iter().enumerate() {
        let mark = if i == default { ">" } else { " " };
        println!("  {GRAY}{mark}{RESET} {WHITE}{:>2}. {label}{RESET}", i + 1);
    }
    loop {
        print!(
            "{YELLOW}[sparkmc]{RESET} choose 1-{} {GRAY}[{}]{RESET}\n",
            labels.len(),
            default + 1
        );
        prompt_prefix();
        let mut line = String::new();
        if io::stdin().read_line(&mut line).is_err() {
            return None;
        }
        let trimmed = line.trim();
        if trimmed.is_empty() {
            return Some(default);
        }
        if let Ok(n) = trimmed.parse::<usize>() {
            if (1..=labels.len()).contains(&n) {
                return Some(n - 1);
            }
        }
        err(&format!("enter a number from 1 to {}", labels.len()));
    }
}

fn confirm(prompt: &str, default_yes: bool) -> bool {
    let hint = if default_yes { "Y/n" } else { "y/N" };
    print!("{YELLOW}[sparkmc]{RESET} {WHITE}{prompt}{RESET} {GRAY}[{hint}]{RESET}\n");
    prompt_prefix();
    let mut line = String::new();
    if io::stdin().read_line(&mut line).is_err() {
        return default_yes;
    }
    let a = line.trim().to_ascii_lowercase();
    if a.is_empty() {
        return default_yes;
    }
    a.starts_with('y') || a.starts_with('д')
}

fn read_line(prompt: &str) -> Option<String> {
    print!("{YELLOW}[sparkmc]{RESET} {WHITE}{prompt}{RESET}\n");
    prompt_prefix();
    let mut line = String::new();
    if io::stdin().lock().read_line(&mut line).is_err() {
        return None;
    }
    Some(line.trim_end_matches(['\r', '\n']).to_string())
}

fn wait_enter() {
    println!("{GRAY}press Enter to close{RESET}");
    prompt_prefix();
    let mut line = String::new();
    let _ = io::stdin().read_line(&mut line);
}

fn row(key: &str, value: &str) {
    println!("  {GRAY}{key:<10}{RESET} {WHITE}{value}{RESET}");
}

fn yes_no(v: bool) -> &'static str {
    if v { "yes" } else { "no" }
}

fn info(msg: &str) {
    println!("{GREEN}[sparkmc]{RESET} {WHITE}{msg}{RESET}");
}

fn err(msg: &str) {
    println!("{RED}[sparkmc]{RESET} {msg}");
}
