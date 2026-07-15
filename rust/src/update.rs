use std::cmp::Ordering;
use std::env;
use std::fs;
use std::io::{self, BufRead, Write};

use serde::Deserialize;

use crate::{net, util};

const RESET: &str = "\x1b[0m";
const WHITE: &str = "\x1b[38;2;216;222;233m";
const GRAY: &str = "\x1b[38;2;118;124;138m";
const GREEN: &str = "\x1b[38;2;138;214;140m";
const RED: &str = "\x1b[38;2;232;118;110m";
const YELLOW: &str = "\x1b[38;2;232;192;102m";

const LATEST: &str = "https://api.github.com/repos/T1REI/sparkmc/releases/latest";

#[derive(Deserialize)]
struct Release {
    tag_name: String,
    assets: Vec<Asset>,
}

#[derive(Deserialize)]
struct Asset {
    name: String,
    browser_download_url: String,
}

/// Checks GitHub releases on start and offers to self-update.
/// Any network failure is silent - sparkmc just starts as usual.
pub fn check_and_offer() {
    cleanup_old_binary();

    let release: Release = match net::get_json(LATEST) {
        Ok(r) => r,
        Err(_) => return,
    };
    let latest = release.tag_name.trim_start_matches('v');
    if util::cmp_version(latest, env!("CARGO_PKG_VERSION")) != Ordering::Greater {
        return;
    }
    let asset_name = if cfg!(windows) { "sparkmc.exe" } else { "sparkmc" };
    let url = match release.assets.iter().find(|a| a.name == asset_name) {
        Some(a) => &a.browser_download_url,
        None => return,
    };

    println!(
        "{YELLOW}[sparkmc]{RESET} update {WHITE}{latest}{RESET} is available (you have {})",
        env!("CARGO_PKG_VERSION")
    );
    println!("{YELLOW}[sparkmc]{RESET} {WHITE}update now?{RESET} {GRAY}[Y/n]{RESET}");
    print!("{GREEN}>{RESET} ");
    let _ = io::stdout().flush();
    let mut line = String::new();
    if io::stdin().lock().read_line(&mut line).is_err() {
        return;
    }
    let a = line.trim().to_ascii_lowercase();
    if !(a.is_empty() || a.starts_with('y') || a.starts_with('д')) {
        println!(
            "{GRAY}[sparkmc] skipped, continuing with {}{RESET}",
            env!("CARGO_PKG_VERSION")
        );
        return;
    }

    match apply_update(url, latest) {
        Ok(()) => {
            println!("{GREEN}[sparkmc]{RESET} {WHITE}updated to {latest}, restart to apply{RESET}");
        }
        Err(e) => println!("{RED}[sparkmc]{RESET} update failed: {e}"),
    }
}

fn apply_update(url: &str, latest: &str) -> Result<(), String> {
    let exe = env::current_exe().map_err(|e| format!("cannot locate own binary: {e}"))?;
    let fresh = exe.with_extension("new");
    let old = exe.with_extension("old");

    println!("{GREEN}[sparkmc]{RESET} {WHITE}downloading {latest}...{RESET}");
    net::download_file(url, &fresh)?;

    // A running executable cannot be overwritten, but it can be renamed.
    fs::rename(&exe, &old).map_err(|e| format!("cannot move old binary: {e}"))?;
    if let Err(e) = fs::rename(&fresh, &exe) {
        // roll back so the user still has a working binary
        let _ = fs::rename(&old, &exe);
        return Err(format!("cannot install new binary: {e}"));
    }

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(&exe, fs::Permissions::from_mode(0o755));
    }
    Ok(())
}

/// Removes the leftover `.old` binary from a previous update.
fn cleanup_old_binary() {
    if let Ok(exe) = env::current_exe() {
        let _ = fs::remove_file(exe.with_extension("old"));
    }
}
