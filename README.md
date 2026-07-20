<div align="center">

# ⚡ sparkmc

Console tool that sets up and runs a Minecraft server.
Pick a core, type a version — it downloads everything and keeps a live console attached to the server.

<br>

<a href="https://github.com/T1REI/sparkmc/releases/latest">
  <img src="https://img.shields.io/badge/⬇%20_Download-latest_release-2ea44f?style=for-the-badge&logo=github&logoColor=white" alt="Download">
</a>
<a href="#build-from-source">
  <img src="https://img.shields.io/badge/🔧_Build-from_source-1f6feb?style=for-the-badge" alt="Build">
</a>
<a href="LICENSE">
  <img src="https://img.shields.io/badge/License-MIT-8957e5?style=for-the-badge" alt="MIT License">
</a>

<br><br>

<img src="https://img.shields.io/badge/version-0.0.3-blue?style=flat-square" alt="Version">
<img src="https://img.shields.io/badge/Rust-stable-orange?style=flat-square&logo=rust" alt="Rust">
<img src="https://img.shields.io/badge/Java-17%2B-red?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17+">

</div>

---

## Features

- Cores: **Vanilla, Forge, Fabric, NeoForge, Paper, Purpur**
- Versions are typed as text (`1.20.1`), not picked from numbered lists
- Forge / NeoForge: Recommended or Latest channel
- Config stored in `sparkmc.json` in the server folder
- Checks the required Java version before starting the server
- Update check on start (GitHub Releases) with optional self-update
- Closing the console stops the server — no orphaned JVMs
- Colored console: errors red, warnings yellow, milestones green

## Editions

| | Path | Artifact |
|---|------|----------|
| 🦀 Rust | [`rust/`](rust/) | native `sparkmc` / `sparkmc.exe` |
| ☕ Java | [`java/`](java/) | `sparkmc.jar` |

**Rust** — the wizard also asks about RAM, flag presets (Aikar and others), `--nogui` and auto-restart. If the required Java is missing, choose between automatic download (Temurin) or a custom path to an existing installation (`javaw.exe`, `java.exe` or its folder).

**Java** — no RAM / flags / GUI questions. JVM flags you launch the jar with are forwarded to the server JVM, extra arguments to the server:

```bash
java -Xmx4G -XX:+UseG1GC -jar sparkmc.jar --nogui
```

It does not download Java — if the server needs a newer one, it tells you which.

## Usage

1. Run the binary/jar in an empty folder
2. Answer the wizard: core → version → (channel for Forge/NeoForge)
3. The server downloads and installs, then the console opens
4. Type server commands at the `>` prompt (`list`, `stop`, ...)
5. Delete `sparkmc.json` to run the wizard again

```text
sparkmc            # wizard, or run the existing server if sparkmc.json exists
sparkmc --run      # skip the wizard, run from sparkmc.json
sparkmc --help
```

Java edition:

```bash
java [jvm flags] -jar sparkmc.jar [server args]
```

## Build from source

**Rust** — stable toolchain ([rustup](https://rustup.rs/)):

```bash
cd rust
cargo build --release
./target/release/sparkmc        # Windows: .\target\release\sparkmc.exe
```

**Java** — JDK 17+:

```bash
cd java
./gradlew jar                   # Windows: .\gradlew.bat jar
java -jar build/libs/sparkmc.jar
```

## `sparkmc.json`

```json
{
  "core": "Paper",
  "version": "1.21.1",
  "channel": null,
  "target": "server.jar",
  "required_java": 21
}
```

The Rust edition additionally stores `flags`, `ram_mb`, `no_gui`, `auto_restart` and `java` (the resolved or custom Java path).

## Changelog

### 0.0.3

Rust edition only:

- When the required Java is not found, the prompt is now a menu: download automatically, enter a custom path, or cancel
- Custom path accepts `javaw.exe`, `java.exe` or a Java installation folder; `javaw` is swapped for the console `java` next to it automatically
- The path is version-checked (`java -version`) before use — pointing Java 17 at a server that needs 21 is rejected with a clear message
- A valid custom path is saved to `sparkmc.json` and reused on next start

### 0.0.2

- Java: removed RAM, flag preset, No GUI and auto-restart wizard steps — flags and arguments come from how you launch the jar
- Java: removed automatic Java download; the version check stays
- Both: update check on start with self-update behind a yes/no prompt

### 0.0.1

- Initial release: setup wizard, live console, six cores, process guard

## Notes

- Setup writes `eula=true` — by using this you agree to the [Minecraft EULA](https://aka.ms/MinecraftEULA)
- Forge cleanup keeps `*-shim.jar` — it's required to launch modern Forge
- On Windows the running jar can't replace itself, so the Java edition applies a self-update right after you close it

## License

[MIT](LICENSE)
