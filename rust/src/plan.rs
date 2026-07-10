use std::fs;
use std::path::Path;

use serde::{Deserialize, Serialize};

use crate::model::{Core, FlagPreset, LaunchTarget, LoaderChannel, ServerConfig};
use crate::setup::Prepared;
use crate::util;

const FILE: &str = "sparkmc.json";

#[derive(Serialize, Deserialize)]
pub struct LaunchPlan {
    #[serde(default = "default_core")]
    pub core: Core,
    #[serde(default)]
    pub version: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub channel: Option<LoaderChannel>,
    #[serde(default = "default_flags")]
    pub flags: FlagPreset,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub custom_flags: String,
    #[serde(default = "default_ram")]
    pub ram_mb: u32,
    #[serde(default = "default_true")]
    pub no_gui: bool,
    #[serde(default = "default_true")]
    pub auto_restart: bool,

    #[serde(default)]
    pub target: String,
    #[serde(default)]
    pub required_java: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub java: Option<String>,

    #[serde(default, skip_serializing_if = "Option::is_none")]
    title: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    program: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    args: Option<Vec<String>>,
}

fn default_core() -> Core {
    Core::Vanilla
}

fn default_flags() -> FlagPreset {
    FlagPreset::Aikar
}

fn default_ram() -> u32 {
    4096
}

fn default_true() -> bool {
    true
}

impl LaunchPlan {
    pub fn from_config(cfg: &ServerConfig, prepared: &Prepared, dir: &Path) -> Self {
        let target = match &prepared.target {
            LaunchTarget::Jar(path) => relative(path, dir),
            LaunchTarget::ArgFile(path) => format!("@{}", relative(path, dir)),
        };
        Self {
            core: cfg.core,
            version: cfg.version.clone(),
            channel: cfg.channel,
            flags: cfg.preset,
            custom_flags: cfg.custom_flags.clone(),
            ram_mb: cfg.ram_mb,
            no_gui: cfg.no_gui,
            auto_restart: cfg.auto_restart,
            target,
            required_java: prepared.required_java,
            java: None,
            title: None,
            program: None,
            args: None,
        }
    }

    pub fn title(&self) -> String {
        self.title
            .clone()
            .unwrap_or_else(|| format!("sparkmc - {} {}", self.core.label(), self.version))
    }

    pub fn program(&self) -> String {
        self.java
            .clone()
            .or_else(|| self.program.clone())
            .unwrap_or_else(|| "java".to_string())
    }

    pub fn args(&self) -> Vec<String> {
        let heap = util::heap_mb(self.ram_mb.max(1024)).max(util::MIN_HEAP_MB);
        let mut args: Vec<String> = Vec::new();
        args.extend(self.flags.jvm_flags().iter().map(|f| f.to_string()));
        args.push(format!("-Xms{heap}M"));
        args.push(format!("-Xmx{heap}M"));
        if matches!(self.flags, FlagPreset::Custom) {
            args.extend(self.custom_flags.split_whitespace().map(str::to_string));
        }

        let target = self.target.trim();
        if target.starts_with('@') {
            args.push(target.to_string());
        } else if !target.is_empty() {
            args.push("-jar".to_string());
            args.push(target.to_string());
        } else if let Some(legacy) = &self.args {
            let mut out = legacy.clone();
            if self.no_gui {
                let nogui = self.core.nogui_arg();
                if !out.iter().any(|a| a == nogui) {
                    out.push(nogui.to_string());
                }
            }
            return out;
        }

        if self.no_gui {
            args.push(self.core.nogui_arg().to_string());
        }
        args
    }

    pub fn save(&self, dir: &Path) -> Result<(), String> {
        let json = serde_json::to_string_pretty(self).map_err(|e| e.to_string())?;
        fs::write(dir.join(FILE), json).map_err(|e| format!("cannot write {FILE}: {e}"))
    }

    pub fn load(dir: &Path) -> Result<Self, String> {
        let json =
            fs::read_to_string(dir.join(FILE)).map_err(|e| format!("cannot read {FILE}: {e}"))?;
        let mut plan: Self =
            serde_json::from_str(&json).map_err(|e| format!("invalid {FILE}: {e}"))?;

        if plan.target.is_empty() {
            if let Some(args) = &plan.args {
                if let Some(t) = extract_target_from_args(args) {
                    plan.target = t;
                }
            }
        }
        if plan.ram_mb == 0 {
            plan.ram_mb = 4096;
        }
        Ok(plan)
    }
}

pub fn exists(dir: &Path) -> bool {
    dir.join(FILE).is_file()
}

fn relative(path: &Path, dir: &Path) -> String {
    path.strip_prefix(dir)
        .unwrap_or(path)
        .to_string_lossy()
        .into_owned()
}

fn extract_target_from_args(args: &[String]) -> Option<String> {
    for (i, a) in args.iter().enumerate() {
        if a == "-jar" {
            return args.get(i + 1).cloned();
        }
        if a.starts_with('@') {
            return Some(a.clone());
        }
    }
    None
}
