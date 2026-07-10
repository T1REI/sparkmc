use std::collections::BTreeMap;
use std::path::Path;

use serde::Deserialize;

use super::{
    Provider, argfile_name, cleanup_installer_junk, download, find_file, get_json, remove_if_exists,
    run_installer,
};
use crate::event::Reporter;
use crate::model::{LaunchTarget, LoaderChannel};
use crate::util;

const VERSIONS: &str =
    "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";
const MAVEN: &str = "https://maven.neoforged.net/releases/net/neoforged/neoforge";

pub struct NeoForge;

#[derive(Deserialize)]
struct VersionList {
    versions: Vec<String>,
}

fn neoforge_to_mc(nf: &str) -> Option<String> {
    let mut parts = nf.split('.');
    let major = parts.next()?;
    let minor = parts.next()?;
    if !major.chars().all(|c| c.is_ascii_digit()) || !minor.chars().all(|c| c.is_ascii_digit()) {
        return None;
    }
    if minor == "0" {
        Some(format!("1.{major}"))
    } else {
        Some(format!("1.{major}.{minor}"))
    }
}

fn is_prerelease(nf: &str) -> bool {
    let lower = nf.to_ascii_lowercase();
    lower.contains("beta") || lower.contains("alpha") || lower.contains("rc")
}

fn builds_per_mc(versions: Vec<String>) -> BTreeMap<String, Vec<String>> {
    let mut map: BTreeMap<String, Vec<String>> = BTreeMap::new();
    for nf in versions {
        let Some(mc) = neoforge_to_mc(&nf) else {
            continue;
        };
        map.entry(mc).or_default().push(nf);
    }
    for builds in map.values_mut() {
        builds.sort_by(|a, b| util::cmp_version(a, b));
    }
    map
}

fn pick_build(builds: &[String], channel: LoaderChannel) -> Option<String> {
    match channel {
        LoaderChannel::Latest => builds.last().cloned(),
        LoaderChannel::Recommended => builds
            .iter()
            .rev()
            .find(|b| !is_prerelease(b))
            .cloned()
            .or_else(|| builds.last().cloned()),
    }
}

impl Provider for NeoForge {
    fn versions(&self) -> Result<Vec<String>, String> {
        let list: VersionList = get_json(VERSIONS)?;
        let mut mc: Vec<String> = builds_per_mc(list.versions).into_keys().collect();
        mc.sort_by(|a, b| util::cmp_version(b, a));
        Ok(mc)
    }

    fn prepare(
        &self,
        version: &str,
        channel: Option<LoaderChannel>,
        dir: &Path,
        rep: &Reporter,
    ) -> Result<LaunchTarget, String> {
        let list: VersionList = get_json(VERSIONS)?;
        let channel = channel.unwrap_or(LoaderChannel::Recommended);
        let mut map = builds_per_mc(list.versions);
        let builds = map
            .remove(version)
            .ok_or_else(|| format!("no neoforge build for {version}"))?;
        let neoforge = pick_build(&builds, channel)
            .ok_or_else(|| format!("no neoforge build for {version} ({})", channel.label()))?;

        rep.log(format!("Using NeoForge {neoforge} ({})", channel.label()));

        let installer = dir.join(format!("neoforge-{neoforge}-installer.jar"));
        let url = format!("{MAVEN}/{neoforge}/neoforge-{neoforge}-installer.jar");
        download(&url, &installer, rep)?;
        run_installer(dir, &installer, rep)?;

        remove_if_exists(&installer);
        remove_if_exists(&dir.join(format!("neoforge-{neoforge}-installer.jar.log")));
        cleanup_installer_junk(dir, rep);

        find_file(dir, argfile_name())
            .map(LaunchTarget::ArgFile)
            .ok_or_else(|| "could not locate neoforge args file after install".into())
    }
}
