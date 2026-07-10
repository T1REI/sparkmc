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

const PROMOS: &str =
    "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
const MAVEN: &str = "https://maven.minecraftforge.net/net/minecraftforge/forge";

pub struct Forge;

#[derive(Deserialize)]
struct Promotions {
    promos: BTreeMap<String, String>,
}

impl Provider for Forge {
    fn versions(&self) -> Result<Vec<String>, String> {
        let data: Promotions = get_json(PROMOS)?;
        let mut mc: Vec<String> = data
            .promos
            .keys()
            .filter_map(|k| k.rsplit_once('-').map(|(v, _)| v.to_string()))
            .collect();
        mc.sort_by(|a, b| util::cmp_version(b, a));
        mc.dedup();
        Ok(mc)
    }

    fn prepare(
        &self,
        version: &str,
        channel: Option<LoaderChannel>,
        dir: &Path,
        rep: &Reporter,
    ) -> Result<LaunchTarget, String> {
        let data: Promotions = get_json(PROMOS)?;
        let channel = channel.unwrap_or(LoaderChannel::Recommended);
        let forge = resolve_promo(&data.promos, version, channel)
            .ok_or_else(|| format!("no forge build for {version} ({})", channel.label()))?;

        let full = format!("{version}-{forge}");
        let installer = dir.join(format!("forge-{full}-installer.jar"));
        let url = format!("{MAVEN}/{full}/forge-{full}-installer.jar");
        download(&url, &installer, rep)?;
        run_installer(dir, &installer, rep)?;

        remove_if_exists(&installer);
        remove_if_exists(&dir.join(format!("forge-{full}-installer.jar.log")));
        cleanup_installer_junk(dir, rep);

        if let Some(args) = find_file(dir, argfile_name()) {
            rep.log("Using generated args file");
            return Ok(LaunchTarget::ArgFile(args));
        }
        let legacy = dir.join(format!("forge-{full}.jar"));
        if legacy.exists() {
            return Ok(LaunchTarget::Jar(legacy));
        }
        let universal = dir.join(format!("forge-{full}-universal.jar"));
        if universal.exists() {
            return Ok(LaunchTarget::Jar(universal));
        }
        Err("could not locate forge launch target after install".into())
    }
}

fn resolve_promo(
    promos: &BTreeMap<String, String>,
    version: &str,
    channel: LoaderChannel,
) -> Option<String> {
    let primary = format!("{version}-{}", channel.key());
    let fallback = match channel {
        LoaderChannel::Recommended => format!("{version}-latest"),
        LoaderChannel::Latest => format!("{version}-recommended"),
    };
    promos
        .get(&primary)
        .or_else(|| promos.get(&fallback))
        .cloned()
}
