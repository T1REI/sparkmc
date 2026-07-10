use std::path::Path;

use serde::Deserialize;

use super::{Provider, download, get_json};
use crate::event::Reporter;
use crate::model::{LaunchTarget, LoaderChannel};

const BASE: &str = "https://api.purpurmc.org/v2/purpur";

pub struct Purpur;

#[derive(Deserialize)]
struct Project {
    versions: Vec<String>,
}

#[derive(Deserialize)]
struct VersionInfo {
    builds: BuildRef,
}

#[derive(Deserialize)]
struct BuildRef {
    latest: String,
}

impl Provider for Purpur {
    fn versions(&self) -> Result<Vec<String>, String> {
        let project: Project = get_json(BASE)?;
        let mut versions = project.versions;
        versions.reverse();
        Ok(versions)
    }

    fn prepare(
        &self,
        version: &str,
        _channel: Option<LoaderChannel>,
        dir: &Path,
        rep: &Reporter,
    ) -> Result<LaunchTarget, String> {
        let info: VersionInfo = get_json(&format!("{BASE}/{version}"))?;
        let url = format!("{BASE}/{version}/{}/download", info.builds.latest);
        let path = dir.join("server.jar");
        download(&url, &path, rep)?;
        Ok(LaunchTarget::Jar(path))
    }
}
