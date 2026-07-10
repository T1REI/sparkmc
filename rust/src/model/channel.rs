use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, PartialEq, Eq, Debug, Serialize, Deserialize)]
#[serde(rename_all = "PascalCase")]
pub enum LoaderChannel {
    Recommended,
    Latest,
}

impl LoaderChannel {
    pub const fn all() -> [LoaderChannel; 2] {
        [LoaderChannel::Recommended, LoaderChannel::Latest]
    }

    pub const fn label(self) -> &'static str {
        match self {
            LoaderChannel::Recommended => "Recommended",
            LoaderChannel::Latest => "Latest",
        }
    }

    pub const fn key(self) -> &'static str {
        match self {
            LoaderChannel::Recommended => "recommended",
            LoaderChannel::Latest => "latest",
        }
    }
}
