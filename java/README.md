# sparkmc (Java)

Pure **Java 17** console edition of **sparkmc** (fat JAR).

## Build

Needs **JDK 17+**. System Gradle not required.

```bash
./gradlew jar
```

```powershell
.\gradlew.bat jar
```

Output:

```text
build/libs/sparkmc.jar
```

## Run

```bash
java -jar build/libs/sparkmc.jar
```

## Requirements

- [JDK 17+](https://adoptium.net/)
- Network for downloads

See the [root README](../README.md) for shared features and `sparkmc.json` format.
