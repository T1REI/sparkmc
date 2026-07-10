use std::io::{self, Write};
use std::sync::Mutex;

pub struct Reporter {
    lock: Mutex<()>,
}

impl Reporter {
    pub fn new() -> Self {
        Self {
            lock: Mutex::new(()),
        }
    }

    pub fn log(&self, msg: impl AsRef<str>) {
        let _guard = self.lock.lock().ok();
        let mut out = io::stdout();
        let _ = writeln!(out, "\x1b[38;2;138;214;140m[sparkmc]\x1b[0m {}", msg.as_ref());
        let _ = out.flush();
    }
}

impl Default for Reporter {
    fn default() -> Self {
        Self::new()
    }
}
