# Publish monorepo (Rust + Java) to GitHub

## 1. Create empty repo on GitHub

https://github.com/new

| Field | Value |
|-------|--------|
| Name | `sparkmc` |
| Description | `Minecraft server setup & console — Rust + Java` |
| Public | yes |
| Add README / gitignore / license | **No** |

Copy URL: `https://github.com/YOUR_USER/sparkmc.git`

---

## 2. Push from monorepo root

Use the **monorepo folder** that contains both `rust/` and `java/`  
(workspace path: `sparkmc-repo`, or assemble the same layout on your PC).

### Windows PowerShell

```powershell
cd D:\AllFiles\Projects\Rust\sparkmc-repo
# if you keep editions separately, create layout first:
# mkdir sparkmc-repo; move sparkmc sparkmc-repo\rust; move sparkmc-java sparkmc-repo\java

git init
git branch -M main
git add .
git status
git commit -m "Initial commit: sparkmc monorepo (rust + java)"

git remote add origin https://github.com/YOUR_USER/sparkmc.git
git push -u origin main
```

### Linux / macOS

```bash
cd /path/to/sparkmc-repo
git init
git branch -M main
git add .
git commit -m "Initial commit: sparkmc monorepo (rust + java)"
git remote add origin https://github.com/YOUR_USER/sparkmc.git
git push -u origin main
```

---

## 3. If your projects are still separate folders

```powershell
cd D:\AllFiles\Projects\Rust
mkdir sparkmc-repo
# copy (not cut) so you keep backups
xcopy /E /I sparkmc sparkmc-repo\rust
xcopy /E /I sparkmc-java sparkmc-repo\java
# then copy root docs from this package: README, LICENSE, .gitignore, .github, CONTRIBUTING
cd sparkmc-repo
# continue with git init ...
```

Root must look like:

```text
sparkmc-repo/
  README.md
  LICENSE
  .gitignore
  CONTRIBUTING.md
  .github/workflows/build.yml
  rust/
  java/
```

---

## 4. GitHub About

- **Description:** `Minecraft server setup & console — Rust binary + Java JAR`
- **Topics:** `minecraft` `minecraft-server` `paper` `forge` `fabric` `neoforge` `rust` `java` `cli`

---

## 5. Releases (optional)

Build both:

```bash
cd rust && cargo build --release && cd ..
cd java && ./gradlew jar && cd ..
```

Create release `v0.1.0` and attach:

- `rust/target/release/sparkmc` / `sparkmc.exe`
- `java/build/libs/sparkmc.jar`

CI (Actions) also builds both on every push to `main`.

---

## 6. Auth tips

- HTTPS push: use a **Personal Access Token**, not account password  
- Or setup SSH key and use `git@github.com:USER/sparkmc.git`
