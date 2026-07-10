mod commands;
mod console;
mod event;
mod java;
mod model;
mod net;
mod plan;
mod setup;
mod term;
mod util;
mod wizard;

use std::env;
use std::path::PathBuf;
use std::process;

fn main() {
    let dir = env::current_dir().unwrap_or_else(|_| PathBuf::from("."));
    let mut args = env::args().skip(1);

    match args.next().as_deref() {
        Some("--install-java") => {
            let major = args
                .next()
                .and_then(|s| s.parse::<u32>().ok())
                .unwrap_or(0);
            if major == 0 {
                eprintln!("usage: sparkmc --install-java <major>");
                process::exit(2);
            }
            match java::elevated_install_entry(major) {
                Ok(()) => process::exit(0),
                Err(e) => {
                    eprintln!("[sparkmc] {e}");
                    process::exit(1);
                }
            }
        }
        Some("--run") => {
            console::run(&dir);
        }
        Some("--help") | Some("-h") => {
            print_help();
        }
        Some(other) => {
            eprintln!("unknown argument: {other}");
            print_help();
            process::exit(2);
        }
        None => {
            if plan::exists(&dir) {
                console::run(&dir);
            } else {
                wizard::run(&dir);
            }
        }
    }
}

fn print_help() {
    println!(
        "sparkmc - Minecraft server setup & console\n\
         \n\
         Usage:\n\
           sparkmc                 interactive setup or run existing server\n\
           sparkmc --run           run server from sparkmc.json\n\
           sparkmc --install-java N  elevated Java N install (internal)\n\
           sparkmc --help          show this help"
    );
}
