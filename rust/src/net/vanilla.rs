use std::path::Path;

use serde::Deserialize;

use super::{Provider, download, get_json};
use crate::event::Reporter;
use crate::model::{LaunchTarget, LoaderChannel};

const MANIFEST: &str = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

pub struct Vanilla;

#[derive(Deserialize)]
struct Manifest {
    versions: Vec<Entry>,
}

#[derive(Deserialize)]
struct Entry {
    id: String,
    #[serde(rename = "type")]
    kind: String,
    url: String,
}

#[derive(Deserialize)]
struct VersionMeta {
    downloads: Downloads,
}

#[derive(Deserialize)]
struct Downloads {
    server: Option<Artifact>,
}

#[derive(Deserialize)]
struct Artifact {
    url: String,
}

impl Provider for Vanilla {
    fn versions(&self) -> Result<Vec<String>, String> {
        let manifest: Manifest = get_json(MANIFEST)?;
        Ok(manifest
            .versions
            .into_iter()
            .filter(|v| v.kind == "release")
            .map(|v| v.id)
            .collect())
    }

    fn prepare(
        &self,
        version: &str,
        _channel: Option<LoaderChannel>,
        dir: &Path,
        rep: &Reporter,
    ) -> Result<LaunchTarget, String> {
        let manifest: Manifest = get_json(MANIFEST)?;
        let entry = manifest
            .versions
            .into_iter()
            .find(|v| v.id == version)
            .ok_or_else(|| format!("version {version} not found"))?;
        let meta: VersionMeta = get_json(&entry.url)?;
        let server = meta
            .downloads
            .server
            .ok_or_else(|| format!("no server jar available for {version}"))?;
        let path = dir.join("server.jar");
        download(&server.url, &path, rep)?;
        Ok(LaunchTarget::Jar(path))
    }
}
