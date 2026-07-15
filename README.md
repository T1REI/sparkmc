# sparkmc

Console tool that sets up and runs a Minecraft server. Pick a core, type a version, and it downloads everything, writes the config and keeps a live console attached to the server process.

Two editions in one repo, same idea:

| Path | Language | Artifact |
|------|----------|----------|
| `rust/` | Rust | native binary `sparkmc` / `sparkmc.exe` |
| `java/` | Java 17+ | `sparkmc.jar` |

Current version: **0.0.2**

## Features

- Cores: Vanilla, Forge, Fabric, NeoForge, Paper, Purpur
- Versions are typed as text (`1.20.1`), not picked from numbered lists
- Forge / NeoForge: Recommended or Latest channel
- Paper via Fill API v3
- Config stored in `sparkmc.json` in the server folder
- Checks that the required Java version is present before starting the server
- Update check on start against GitHub Releases, with optional self-update (you can always say no)
- Closing the console stops the server process — no orphaned JVMs

Edition differences:

- **Java edition**: no RAM / flags / GUI questions in the wizard. The JVM flags you use to launch `sparkmc.jar` are forwarded to the server JVM, and extra arguments are forwarded to the server. Example:

  ```bash
  java -Xmx4G -XX:+UseG1GC -jar sparkmc.jar --nogui
  ```

  It does not download Java for you — if the server needs a newer Java, it tells you which one and where to get it.

- **Rust edition**: the wizard asks about RAM, flag presets (Aikar and others), `--nogui` and auto-restart, and can download the required Java (Temurin) if it's missing.

## Changelog

### 0.0.2

- Java: removed RAM, flag preset, No GUI and auto-restart steps from the wizard. Flags and arguments now come from how you launch the jar itself and are forwarded to the server process.
- Java: removed automatic Java download. The check for the required Java version stays — if it's missing you get a message with the needed version.
- Java: removed `Http.java`, HTTP helpers merged into `NetUtil`.
- Both: added update check on start (GitHub Releases) and self-update with a yes/no prompt.
- Docs merged into this single README.

### 0.0.1

- Initial release: setup wizard, live console, six cores, process guard.

## Build & run

### Rust

```bash
cd rust
cargo build --release
./target/release/sparkmc        # Windows: .\target\release\sparkmc.exe
```

Needs a stable Rust toolchain ([rustup](https://rustup.rs/)).

### Java

```bash
cd java
./gradlew jar                   # Windows: .\gradlew.bat jar
java -jar build/libs/sparkmc.jar
```

Needs JDK 17+.

## Usage

1. Run the binary/jar in an empty folder
2. Answer the wizard: core → version → (channel for Forge/NeoForge)
3. The server downloads and installs, then the console opens
4. Type server commands at the `>` prompt (`list`, `stop`, ...)
5. Delete `sparkmc.json` to run the wizard again

### CLI

```text
sparkmc            # wizard, or run the existing server if sparkmc.json exists
sparkmc --run      # skip the wizard, run from sparkmc.json
sparkmc --help
```

Java edition:

```bash
java [jvm flags] -jar sparkmc.jar [server args]
java -jar sparkmc.jar --run
java -jar sparkmc.jar --help
```

## `sparkmc.json`

Java edition:

```json
{
  "core": "Paper",
  "version": "1.21.1",
  "channel": null,
  "target": "server.jar",
  "required_java": 21
}
```

Rust edition additionally stores `flags`, `ram_mb`, `no_gui`, `auto_restart` and `java` since its wizard configures them.

## Notes

- Setup writes `eula=true` — by using this you agree to the [Minecraft EULA](https://aka.ms/MinecraftEULA).
- Forge cleanup keeps `*-shim.jar` — it's required to launch modern Forge.
- Self-update replaces the binary/jar in place. On Windows the running jar can't be replaced directly, so the Java edition applies the update right after you close it.

## License

MIT — see [LICENSE](LICENSE).
