use std::path::PathBuf;

use super::{Core, FlagPreset, LoaderChannel};

#[derive(Clone, Debug)]
pub struct ServerConfig {
    pub core: Core,
    pub version: String,
    pub channel: Option<LoaderChannel>,
    pub preset: FlagPreset,
    pub custom_flags: String,
    pub ram_mb: u32,
    pub no_gui: bool,
    pub auto_restart: bool,
}

#[derive(Clone, Debug)]
pub enum LaunchTarget {
    Jar(PathBuf),
    ArgFile(PathBuf),
}
