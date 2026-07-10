use std::collections::BTreeMap;
use std::path::Path;

use serde::Deserialize;

use super::{Provider, download, get_json};
use crate::event::Reporter;
use crate::model::{LaunchTarget, LoaderChannel};
use crate::util;

const BASE: &str = "https://fill.papermc.io/v3/projects/paper";

pub struct Paper;

#[derive(Deserialize)]
struct Project {
    versions: BTreeMap<String, Vec<String>>,
}

#[derive(Deserialize)]
struct Build {
    id: i64,
    channel: String,
    downloads: BTreeMap<String, Download>,
}

#[derive(Deserialize)]
struct Download {
    url: String,
}

impl Provider for Paper {
    fn versions(&self) -> Result<Vec<String>, String> {
        let project: Project = get_json(BASE)?;
        let mut versions: Vec<String> = project.versions.into_values().flatten().collect();
        versions.retain(|v| is_release_version(v));
        versions.sort_by(|a, b| util::cmp_version(b, a));
        versions.dedup();
        Ok(versions)
    }

    fn prepare(
        &self,
        version: &str,
        _channel: Option<LoaderChannel>,
        dir: &Path,
        rep: &Reporter,
    ) -> Result<LaunchTarget, String> {
        let builds: Vec<Build> = get_json(&format!("{BASE}/versions/{version}/builds"))?;
        if builds.is_empty() {
            return Err(format!("no builds for {version}"));
        }

        let build = builds
            .iter()
            .find(|b| b.channel.eq_ignore_ascii_case("STABLE"))
            .or_else(|| builds.first())
            .ok_or_else(|| format!("no builds for {version}"))?;

        let artifact = build
            .downloads
            .get("server:default")
            .or_else(|| build.downloads.values().next())
            .ok_or_else(|| format!("no download for paper {version} build {}", build.id))?;

        rep.log(format!(
            "Using Paper {} build {} ({})",
            version, build.id, build.channel
        ));

        let path = dir.join("server.jar");
        download(&artifact.url, &path, rep)?;
        Ok(LaunchTarget::Jar(path))
    }
}

fn is_release_version(v: &str) -> bool {
    let lower = v.to_ascii_lowercase();
    !(lower.contains("pre")
        || lower.contains("rc")
        || lower.contains("snapshot")
        || lower.contains("alpha")
        || lower.contains("beta"))
}
