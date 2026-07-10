use std::path::Path;

use serde::Deserialize;

use super::{Provider, download, get_json};
use crate::event::Reporter;
use crate::model::{LaunchTarget, LoaderChannel};

const META: &str = "https://meta.fabricmc.net/v2/versions";

pub struct Fabric;

#[derive(Deserialize)]
struct Game {
    version: String,
    stable: bool,
}

#[derive(Deserialize)]
struct Named {
    version: String,
}

impl Provider for Fabric {
    fn versions(&self) -> Result<Vec<String>, String> {
        let games: Vec<Game> = get_json(&format!("{META}/game"))?;
        Ok(games
            .into_iter()
            .filter(|g| g.stable)
            .map(|g| g.version)
            .collect())
    }

    fn prepare(
        &self,
        version: &str,
        _channel: Option<LoaderChannel>,
        dir: &Path,
        rep: &Reporter,
    ) -> Result<LaunchTarget, String> {
        let loaders: Vec<Named> = get_json(&format!("{META}/loader"))?;
        let loader = loaders
            .first()
            .ok_or("no fabric loader available")?
            .version
            .clone();
        let installers: Vec<Named> = get_json(&format!("{META}/installer"))?;
        let installer = installers
            .first()
            .ok_or("no fabric installer available")?
            .version
            .clone();
        let url = format!("{META}/loader/{version}/{loader}/{installer}/server/jar");
        let path = dir.join("server.jar");
        download(&url, &path, rep)?;
        Ok(LaunchTarget::Jar(path))
    }
}
