# sparkmc

**Fast console tool for Minecraft server setup & management.**

Two implementations in one monorepo:

| Path | Language | Artifact |
|------|----------|----------|
| [`rust/`](rust/) | Rust | native binary `sparkmc` |
| [`java/`](java/) | Java 17 | fat JAR `sparkmc.jar` |

[![Rust](https://img.shields.io/badge/Rust-2021-orange?logo=rust)](https://www.rust-lang.org/)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## What it does

Interactive **console wizard** + **live server console**:

- Cores: **Vanilla · Forge · Fabric · NeoForge · Paper · Purpur**
- Type versions as text (`1.20.1`), not list indexes  
- Forge / NeoForge: **Recommended** or **Latest**  
- Paper via **Fill API v3**  
- Editable `sparkmc.json` (settings first)  
- Detect / download required Java (Temurin)  
- Closing the console stops the server process (hooks + watchdog)

Both editions share the same UX and config shape.

---

## Choose an edition

| | **Rust** | **Java** |
|--|----------|----------|
| Best for | smallest native binary, max speed | run anywhere with JDK, no cargo |
| Needs | Rust toolchain | JDK 17+ |
| Output | `sparkmc` / `sparkmc.exe` | `sparkmc.jar` |
| Folder | [`rust/`](rust/) | [`java/`](java/) |

---

## Quick start — Rust

```bash
cd rust
cargo build --release
# binary: rust/target/release/sparkmc  (+ .exe on Windows)

./target/release/sparkmc
# Windows:
# .\target\release\sparkmc.exe
```

---

## Quick start — Java

```bash
cd java
./gradlew jar          # Linux / macOS
# Windows: .\gradlew.bat jar

java -jar build/libs/sparkmc.jar
```

---

## Usage (both)

1. Run the binary/jar in an empty server folder  
2. Wizard: core → version → (channel) → flags → RAM → options  
3. Server downloads & installs  
4. Console stays open (`>` prompt for commands)  
5. Delete `sparkmc.json` to reconfigure  

```text
[sparkmc] version [1.21.1]
> 1.20.1
[sparkmc] selected 1.20.1
```

```text
> list
> stop
```

### CLI

```text
sparkmc                 # wizard or run existing plan
sparkmc --run           # force console from sparkmc.json
sparkmc --help
```

Java:

```bash
java -jar sparkmc.jar
java -jar sparkmc.jar --run
java -jar sparkmc.jar --help
```

---

## `sparkmc.json`

Created in the working directory. Settings first for easy edits:

```json
{
  "core": "Paper",
  "version": "1.21.1",
  "channel": null,
  "flags": "Aikar",
  "ram_mb": 4096,
  "no_gui": true,
  "auto_restart": false,
  "target": "server.jar",
  "required_java": 21,
  "java": null
}
```

Compatible idea across both implementations (field names may differ slightly by language serialization, but purpose is the same).

---

## Repository layout

```text
sparkmc/
├── README.md                 ← you are here
├── LICENSE
├── CONTRIBUTING.md
├── .gitignore
├── .github/workflows/
│   └── build.yml             ← CI for rust + java
├── rust/                     ← native Rust edition
│   ├── Cargo.toml
│   └── src/
└── java/                     ← Java 17 edition
    ├── build.gradle.kts
    ├── gradlew*
    └── src/main/java/sparkmc/
```

---

## Requirements

**Rust edition:** [rustup](https://rustup.rs/) (stable)  
**Java edition:** [JDK 17+](https://adoptium.net/)  
**Both:** network access for downloads  

---

## Notes

- Setup writes `eula=true` — you agree to the [Minecraft EULA](https://aka.ms/MinecraftEULA).
- Forge cleanup keeps `*-shim.jar` (required to launch modern Forge).
- Process guard aims to prevent orphaned server JVMs when the console is closed (Windows / Linux / macOS).

---

## License

MIT — see [LICENSE](LICENSE).
