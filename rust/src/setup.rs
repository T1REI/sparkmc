use std::fs;
use std::path::Path;

use crate::event::Reporter;
use crate::model::{LaunchTarget, ServerConfig};
use crate::net;

const EULA: &str = "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\n#Sat May 09 21:46:40 GMT+04:00 2026\neula=true\n";

pub struct Prepared {
    pub target: LaunchTarget,
    pub required_java: Option<u32>,
}

pub fn run(dir: &Path, cfg: &ServerConfig, rep: &Reporter) -> Result<Prepared, String> {
    rep.log("Accepting EULA (eula.txt)");
    fs::write(dir.join("eula.txt"), EULA).map_err(|e| format!("cannot write eula.txt: {e}"))?;

    let channel_note = cfg
        .channel
        .map(|c| format!(" [{}]", c.label()))
        .unwrap_or_default();
    rep.log(format!(
        "Preparing {} {}{}",
        cfg.core.label(),
        cfg.version,
        channel_note
    ));
    let target = net::provider(cfg.core).prepare(&cfg.version, cfg.channel, dir, rep)?;

    if let Some(folder) = cfg.core.content_folder() {
        let path = dir.join(folder);
        if !path.exists() {
            fs::create_dir_all(&path).map_err(|e| format!("cannot create {folder}/: {e}"))?;
            rep.log(format!("Created {folder}/"));
        }
    }

    let required_java = net::required_java(&cfg.version);
    if let Some(major) = required_java {
        rep.log(format!("Server requires Java {major}"));
    }

    rep.log("Setup complete");
    Ok(Prepared {
        target,
        required_java,
    })
}
