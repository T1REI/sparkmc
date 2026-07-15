mod fabric;
mod forge;
mod neoforge;
mod paper;
mod purpur;
mod vanilla;

use std::fs::{self, File};
use std::path::{Path, PathBuf};
use std::process::Command;

use serde::Deserialize;
use serde::de::DeserializeOwned;

use crate::event::Reporter;
use crate::model::{Core, LaunchTarget, LoaderChannel};

const MOJANG_MANIFEST: &str =
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

pub fn required_java(mc_version: &str) -> Option<u32> {
    let manifest: MojangManifest = get_json(MOJANG_MANIFEST).ok()?;
    let entry = manifest.versions.into_iter().find(|v| v.id == mc_version)?;
    let meta: MojangVersion = get_json(&entry.url).ok()?;
    meta.java_version.map(|j| j.major_version)
}

#[derive(Deserialize)]
struct MojangManifest {
    versions: Vec<MojangEntry>,
}

#[derive(Deserialize)]
struct MojangEntry {
    id: String,
    url: String,
}

#[derive(Deserialize)]
struct MojangVersion {
    #[serde(rename = "javaVersion")]
    java_version: Option<JavaComponent>,
}

#[derive(Deserialize)]
struct JavaComponent {
    #[serde(rename = "majorVersion")]
    major_version: u32,
}

pub trait Provider {
    fn versions(&self) -> Result<Vec<String>, String>;
    fn prepare(
        &self,
        version: &str,
        channel: Option<LoaderChannel>,
        dir: &Path,
        rep: &Reporter,
    ) -> Result<LaunchTarget, String>;
}

pub fn provider(core: Core) -> Box<dyn Provider> {
    match core {
        Core::Vanilla => Box::new(vanilla::Vanilla),
        Core::Forge => Box::new(forge::Forge),
        Core::Fabric => Box::new(fabric::Fabric),
        Core::NeoForge => Box::new(neoforge::NeoForge),
        Core::Paper => Box::new(paper::Paper),
        Core::Purpur => Box::new(purpur::Purpur),
    }
}

fn agent() -> ureq::Agent {
    let mut builder = ureq::AgentBuilder::new().user_agent(concat!(
        "sparkmc/",
        env!("CARGO_PKG_VERSION"),
        " (https://github.com/T1REI/sparkmc)"
    ));
    if let Ok(connector) = native_tls::TlsConnector::new() {
        builder = builder.tls_connector(std::sync::Arc::new(connector));
    }
    builder.build()
}

pub(crate) fn get_json<T: DeserializeOwned>(url: &str) -> Result<T, String> {
    agent()
        .get(url)
        .call()
        .map_err(|e| format!("request failed ({url}): {e}"))?
        .into_json::<T>()
        .map_err(|e| format!("invalid response ({url}): {e}"))
}

pub fn download_file(url: &str, path: &Path) -> Result<(), String> {
    let resp = agent()
        .get(url)
        .call()
        .map_err(|e| format!("download failed: {e}"))?;
    let mut reader = resp.into_reader();
    let mut file = File::create(path).map_err(|e| format!("cannot create {path:?}: {e}"))?;
    std::io::copy(&mut reader, &mut file).map_err(|e| format!("write failed: {e}"))?;
    Ok(())
}

fn download(url: &str, path: &Path, rep: &Reporter) -> Result<(), String> {
    rep.log(format!("Downloading {url}"));
    let resp = agent()
        .get(url)
        .call()
        .map_err(|e| format!("download failed ({url}): {e}"))?;
    let mut reader = resp.into_reader();
    let mut file = File::create(path).map_err(|e| format!("cannot create {path:?}: {e}"))?;
    let bytes = std::io::copy(&mut reader, &mut file).map_err(|e| format!("write failed: {e}"))?;
    rep.log(format!("Saved {} ({} KiB)", path.display(), bytes / 1024));
    Ok(())
}

fn run_installer(dir: &Path, installer: &Path, rep: &Reporter) -> Result<(), String> {
    rep.log("Running installer (--installServer), this may take a while...");
    let output = Command::new("java")
        .arg("-jar")
        .arg(installer)
        .arg("--installServer")
        .current_dir(dir)
        .output()
        .map_err(|e| format!("failed to launch java: {e}"))?;
    if !output.status.success() {
        let tail = String::from_utf8_lossy(&output.stderr);
        return Err(format!("installer exited with error: {}", tail.trim()));
    }
    rep.log("Installer finished");
    Ok(())
}

fn find_file(root: &Path, name: &str) -> Option<PathBuf> {
    let mut stack = vec![root.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let entries = match fs::read_dir(&dir) {
            Ok(e) => e,
            Err(_) => continue,
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
            } else if path.file_name().map(|n| n == name).unwrap_or(false) {
                return Some(path);
            }
        }
    }
    None
}

fn argfile_name() -> &'static str {
    if cfg!(windows) {
        "win_args.txt"
    } else {
        "unix_args.txt"
    }
}

fn remove_if_exists(path: &Path) {
    let _ = fs::remove_file(path);
}

pub(crate) fn cleanup_installer_junk(dir: &Path, rep: &Reporter) {
    let exact = [
        "installer.log",
        "install.log",
        "run.bat",
        "run.sh",
        "user_jvm_args.txt",
        "startserver.bat",
        "startserver.sh",
        "start.bat",
        "start.sh",
        "readme.txt",
        "README.txt",
        "forge-installer.jar",
        "neoforge-installer.jar",
    ];
    for name in exact {
        let path = dir.join(name);
        if path.is_file() && remove_path(&path) {
            rep.log(format!("Removed {name}"));
        }
    }

    let entries = match fs::read_dir(dir) {
        Ok(e) => e,
        Err(_) => return,
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let name = match path.file_name().and_then(|n| n.to_str()) {
            Some(n) => n,
            None => continue,
        };
        let lower = name.to_ascii_lowercase();
        // keep *-shim.jar — modern Forge/NeoForge launchers need it
        let junk = lower.ends_with("-installer.jar")
            || lower.ends_with("-installer.jar.log")
            || lower.ends_with("installer.jar.log")
            || lower.ends_with(".jar.log")
            || lower.ends_with("-installer.log")
            || (lower.starts_with("forge-") && lower.ends_with(".log"))
            || (lower.starts_with("neoforge-") && lower.ends_with(".log"));
        if junk && remove_path(&path) {
            rep.log(format!("Removed {name}"));
        }
    }
}

fn remove_path(path: &Path) -> bool {
    fs::remove_file(path).is_ok()
}
