# Contributing

Thanks for contributing to **sparkmc**.

## Repository layout

| Folder | Stack |
|--------|--------|
| `rust/` | Rust binary |
| `java/` | Java 17 fat JAR |

Prefer matching behavior between editions when you change shared product logic (providers, wizard steps, config fields).

## Setup

### Rust

```bash
cd rust
cargo build
cargo run
```

### Java

```bash
cd java
./gradlew jar
java -jar build/libs/sparkmc.jar
```

Needs **JDK 17+**.

## Guidelines

- Console-only (no GUI frameworks)
- OOP / modular layout, readable names
- No noisy comments unless non-obvious
- Keep security in mind (downloads, process kill, paths)
- If you touch process lifecycle, test console close on Windows **and** Linux when possible

## Pull requests

1. Branch from `main`
2. Scope: rust-only, java-only, or both (say which)
3. Describe *what* and *why*
4. Note provider/API changes (Paper Fill, Forge promos, …)

## Issues

Include:

- Which edition (`rust` / `java`)
- OS + toolchain (`rustc -V` or `java -version`)
- Core + Minecraft version
- Logs / error text
