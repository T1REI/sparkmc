use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use crate::net;

pub fn needed_major_from_line(line: &str) -> Option<u32> {
    let marker = "class file version ";
    let idx = line.find(marker)?;
    let rest = &line[idx + marker.len()..];
    let digits: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
    let class_version: u32 = digits.parse().ok()?;
    Some(class_version.saturating_sub(44))
}

pub fn installed_major(program: &str) -> Option<u32> {
    let output = Command::new(program).arg("-version").output().ok()?;
    let mut text = String::from_utf8_lossy(&output.stderr).into_owned();
    text.push_str(&String::from_utf8_lossy(&output.stdout));
    parse_major(&text)
}

pub fn resolve(major: u32, preferred: Option<&str>) -> Option<PathBuf> {
    if let Some(path) = preferred {
        if installed_major(path) == Some(major) {
            return Some(PathBuf::from(path));
        }
    }

    if installed_major("java") == Some(major) {
        return Some(PathBuf::from("java"));
    }

    if let Some(path) = find_cached(major) {
        return Some(path);
    }

    for candidate in system_candidates(major) {
        if installed_major(&candidate.to_string_lossy()) == Some(major) {
            return Some(candidate);
        }
    }

    None
}

pub fn from_custom_path(input: &str, major: u32) -> Result<PathBuf, String> {
    let cleaned = input.trim().trim_matches('"').trim_matches('\'');
    if cleaned.is_empty() {
        return Err("path is empty".into());
    }
    let path = PathBuf::from(cleaned);
    if !path.exists() {
        return Err(format!("path does not exist: {cleaned}"));
    }
    let candidate = locate_executable(&path)
        .ok_or_else(|| format!("no java executable found at {cleaned}"))?;
    let program = candidate.to_string_lossy().into_owned();
    match installed_major(&program) {
        Some(found) if found == major => Ok(candidate),
        Some(found) => Err(format!("this is Java {found}, but Java {major} is required")),
        None => Err(format!("could not run '{program}' as Java")),
    }
}

fn locate_executable(path: &Path) -> Option<PathBuf> {
    if path.is_dir() {
        return find_java(path);
    }
    if !path.is_file() {
        return None;
    }
    let name = path.file_name()?.to_string_lossy().to_ascii_lowercase();
    if name == "javaw.exe" || name == "javaw" {
        let sibling = path.with_file_name(if cfg!(windows) { "java.exe" } else { "java" });
        if sibling.is_file() {
            return Some(sibling);
        }
    }
    Some(path.to_path_buf())
}

fn parse_major(text: &str) -> Option<u32> {
    let idx = text.find("version \"")?;
    let rest = &text[idx + 9..];
    let version: String = rest.chars().take_while(|c| *c != '"').collect();
    let mut parts = version.split('.');
    let first = parts.next()?;
    if first == "1" {
        parts.next()?.parse().ok()
    } else {
        let digits: String = first.chars().take_while(|c| c.is_ascii_digit()).collect();
        digits.parse().ok()
    }
}

pub fn install(major: u32, log: &dyn Fn(&str)) -> Result<PathBuf, String> {
    if let Some(existing) = resolve(major, None) {
        log("Using existing Java");
        return Ok(existing);
    }

    let dest = cache_root().join(major.to_string());

    if let Err(e) = fs::create_dir_all(&dest) {
        if needs_elevation(&e) {
            log("Need administrator rights to install Java");
            elevate_and_install(major)?;
            return find_cached(major)
                .ok_or_else(|| "java executable not found after elevated install".to_string());
        }
        return Err(format!("cannot create cache dir: {e}"));
    }

    download_and_extract(major, &dest, log)
}

pub fn elevated_install_entry(major: u32) -> Result<(), String> {
    let dest = cache_root().join(major.to_string());
    let log = |m: &str| {
        println!("[sparkmc] {m}");
        let _ = std::io::Write::flush(&mut std::io::stdout());
    };
    log(&format!("Elevated install of Java {major}"));
    fs::create_dir_all(&dest).map_err(|e| format!("cannot create cache dir: {e}"))?;
    download_and_extract(major, &dest, &log)?;
    log("Elevated install finished");
    Ok(())
}

fn download_and_extract(major: u32, dest: &Path, log: &dyn Fn(&str)) -> Result<PathBuf, String> {
    if let Some(existing) = find_java(dest) {
        let candidate = existing.to_string_lossy().into_owned();
        if installed_major(&candidate) == Some(major) {
            log("Using cached Java");
            return Ok(existing);
        }
    }

    let arch = match env::consts::ARCH {
        "x86_64" => "x64",
        "aarch64" => "aarch64",
        other => other,
    };
    let os = if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "mac"
    } else {
        "linux"
    };
    let archive = dest.join(if cfg!(windows) { "pkg.zip" } else { "pkg.tar.gz" });

    let mut downloaded = false;
    for image in ["jre", "jdk"] {
        let url = format!(
            "https://api.adoptium.net/v3/binary/latest/{major}/ga/{os}/{arch}/{image}/hotspot/normal/eclipse"
        );
        log(&format!("Downloading Java {major} ({image})..."));
        match net::download_file(&url, &archive) {
            Ok(()) => {
                downloaded = true;
                break;
            }
            Err(e) => log(&format!("  {image} unavailable: {e}")),
        }
    }
    if !downloaded {
        return Err(format!("no Temurin Java {major} build for {os}/{arch}"));
    }

    log("Extracting...");
    extract(&archive, dest)?;
    let _ = fs::remove_file(&archive);

    find_java(dest).ok_or_else(|| "java executable not found after extraction".to_string())
}

fn needs_elevation(err: &std::io::Error) -> bool {
    match err.kind() {
        std::io::ErrorKind::PermissionDenied => true,
        _ => {
            #[cfg(windows)]
            {
                err.raw_os_error() == Some(5)
            }
            #[cfg(not(windows))]
            {
                false
            }
        }
    }
}

fn elevate_and_install(major: u32) -> Result<(), String> {
    let exe = env::current_exe().map_err(|e| format!("cannot locate sparkmc: {e}"))?;
    let cwd = env::current_dir().unwrap_or_else(|_| PathBuf::from("."));

    #[cfg(windows)]
    {
        elevate_windows(&exe, &cwd, major)
    }

    #[cfg(not(windows))]
    {
        elevate_unix(&exe, &cwd, major)
    }
}

#[cfg(windows)]
fn elevate_windows(exe: &Path, cwd: &Path, major: u32) -> Result<(), String> {
    use std::os::windows::process::CommandExt;

    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    let status = Command::new("powershell")
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-Command",
            &format!(
                "Start-Process -FilePath '{}' -ArgumentList '--install-java','{major}' -WorkingDirectory '{}' -Verb RunAs -Wait",
                escape_ps(exe),
                escape_ps(cwd),
            ),
        ])
        .creation_flags(CREATE_NO_WINDOW)
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status()
        .map_err(|e| format!("failed to request elevation: {e}"))?;

    if status.success() {
        Ok(())
    } else {
        Err("administrator elevation was cancelled or failed".into())
    }
}

#[cfg(windows)]
fn escape_ps(path: &Path) -> String {
    path.display().to_string().replace('\'', "''")
}

#[cfg(not(windows))]
fn elevate_unix(exe: &Path, cwd: &Path, major: u32) -> Result<(), String> {
    let helpers = ["pkexec", "sudo"];
    for helper in helpers {
        let status = Command::new(helper)
            .arg(exe)
            .arg("--install-java")
            .arg(major.to_string())
            .current_dir(cwd)
            .status();
        match status {
            Ok(s) if s.success() => return Ok(()),
            Ok(_) => continue,
            Err(_) => continue,
        }
    }
    Err("could not elevate privileges (pkexec/sudo unavailable)".into())
}

fn cache_root() -> PathBuf {
    #[cfg(windows)]
    {
        let base = env::var("ProgramFiles").unwrap_or_else(|_| "C:\\Program Files".to_string());
        PathBuf::from(base).join("Java")
    }
    #[cfg(not(windows))]
    {
        if let Ok(local) = env::var("XDG_CACHE_HOME") {
            return PathBuf::from(local).join("sparkmc").join("java");
        }
        if let Ok(home) = env::var("HOME") {
            return PathBuf::from(home).join(".cache").join("sparkmc").join("java");
        }
        env::temp_dir().join("sparkmc").join("java")
    }
}

fn find_cached(major: u32) -> Option<PathBuf> {
    let dest = cache_root().join(major.to_string());
    let existing = find_java(&dest)?;
    let candidate = existing.to_string_lossy().into_owned();
    if installed_major(&candidate) == Some(major) {
        Some(existing)
    } else {
        None
    }
}

fn system_candidates(major: u32) -> Vec<PathBuf> {
    let mut out = Vec::new();

    #[cfg(windows)]
    {
        let roots = [
            env::var("ProgramFiles").unwrap_or_else(|_| "C:\\Program Files".into()),
            env::var("ProgramFiles(x86)").unwrap_or_else(|_| "C:\\Program Files (x86)".into()),
            env::var("LOCALAPPDATA")
                .map(|v| format!("{v}\\Programs"))
                .unwrap_or_default(),
        ];
        for root in roots {
            if root.is_empty() {
                continue;
            }
            let base = PathBuf::from(root);
            out.push(base.join("Java").join(format!("jdk-{major}")).join("bin").join("java.exe"));
            out.push(base.join("Java").join(format!("jre-{major}")).join("bin").join("java.exe"));
            out.push(
                base.join("Eclipse Adoptium")
                    .join(format!("jdk-{major}"))
                    .join("bin")
                    .join("java.exe"),
            );
            out.push(
                base.join("Microsoft")
                    .join(format!("jdk-{major}"))
                    .join("bin")
                    .join("java.exe"),
            );
            out.push(
                base.join("Amazon Corretto")
                    .join(format!("jdk{major}"))
                    .join("bin")
                    .join("java.exe"),
            );
        }
        if let Ok(java_home) = env::var("JAVA_HOME") {
            out.push(PathBuf::from(java_home).join("bin").join("java.exe"));
        }
    }

    #[cfg(not(windows))]
    {
        out.push(PathBuf::from(format!("/usr/lib/jvm/java-{major}-openjdk/bin/java")));
        out.push(PathBuf::from(format!("/usr/lib/jvm/java-{major}-temurin/bin/java")));
        out.push(PathBuf::from(format!("/usr/lib/jvm/temurin-{major}-jdk/bin/java")));
        out.push(PathBuf::from(format!("/usr/lib/jvm/jdk-{major}/bin/java")));
        if let Ok(java_home) = env::var("JAVA_HOME") {
            out.push(PathBuf::from(java_home).join("bin").join("java"));
        }
    }

    out
}

fn find_java(root: &Path) -> Option<PathBuf> {
    let exe = if cfg!(windows) { "java.exe" } else { "java" };
    let mut stack = vec![root.to_path_buf()];
    let mut fallback: Option<PathBuf> = None;
    while let Some(dir) = stack.pop() {
        let entries = match fs::read_dir(&dir) {
            Ok(entries) => entries,
            Err(_) => continue,
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
            } else if path.file_name().map(|n| n == exe).unwrap_or(false) {
                let in_bin = path
                    .parent()
                    .and_then(|p| p.file_name())
                    .map(|n| n == "bin")
                    .unwrap_or(false);
                if in_bin {
                    return Some(path);
                }
                fallback.get_or_insert(path);
            }
        }
    }
    fallback
}

#[cfg(windows)]
fn extract(archive: &Path, dest: &Path) -> Result<(), String> {
    let tar_ok = Command::new("tar")
        .arg("-xf")
        .arg(archive)
        .arg("-C")
        .arg(dest)
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    if tar_ok {
        return Ok(());
    }
    let status = Command::new("powershell")
        .args(["-NoProfile", "-NonInteractive", "-Command"])
        .arg(format!(
            "Expand-Archive -Path '{}' -DestinationPath '{}' -Force",
            archive.display(),
            dest.display()
        ))
        .status()
        .map_err(|e| format!("extraction failed: {e}"))?;
    if status.success() {
        Ok(())
    } else {
        Err("extraction failed".to_string())
    }
}

#[cfg(not(windows))]
fn extract(archive: &Path, dest: &Path) -> Result<(), String> {
    let status = Command::new("tar")
        .arg("-xzf")
        .arg(archive)
        .arg("-C")
        .arg(dest)
        .status()
        .map_err(|e| format!("extraction failed: {e}"))?;
    if status.success() {
        Ok(())
    } else {
        Err("extraction failed".to_string())
    }
}
