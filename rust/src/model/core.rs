use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, PartialEq, Eq, Debug, Serialize, Deserialize)]
#[serde(rename_all = "PascalCase")]
pub enum Core {
    Vanilla,
    Forge,
    Fabric,
    NeoForge,
    Paper,
    Purpur,
}

impl Core {
    pub const fn all() -> [Core; 6] {
        [
            Core::Vanilla,
            Core::Forge,
            Core::Fabric,
            Core::NeoForge,
            Core::Paper,
            Core::Purpur,
        ]
    }

    pub const fn label(self) -> &'static str {
        match self {
            Core::Vanilla => "Vanilla",
            Core::Forge => "Forge",
            Core::Fabric => "Fabric",
            Core::NeoForge => "NeoForge",
            Core::Paper => "Paper",
            Core::Purpur => "Purpur",
        }
    }

    pub const fn content_folder(self) -> Option<&'static str> {
        match self {
            Core::Paper | Core::Purpur => Some("plugins"),
            Core::Forge | Core::Fabric | Core::NeoForge => Some("mods"),
            Core::Vanilla => None,
        }
    }

    pub const fn nogui_arg(self) -> &'static str {
        match self {
            Core::Vanilla => "nogui",
            _ => "--nogui",
        }
    }

    pub const fn supports_channel(self) -> bool {
        matches!(self, Core::Forge | Core::NeoForge)
    }
}
