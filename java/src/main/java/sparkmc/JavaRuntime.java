package sparkmc;

import sparkmc.util.Reporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JavaRuntime {
    private JavaRuntime() {}

    public static Optional<Integer> installedMajor(String program) {
        try {
            ProcessBuilder pb = new ProcessBuilder(program, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String text = new String(p.getInputStream().readAllBytes());
            p.waitFor(5, TimeUnit.SECONDS);
            return parseMajor(text);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<Path> resolve(int major, String preferred) {
        if (preferred != null && !preferred.isBlank()) {
            if (installedMajor(preferred).orElse(-1) == major) {
                return Optional.of(Path.of(preferred));
            }
        }
        if (installedMajor("java").orElse(-1) == major) {
            return Optional.of(Path.of("java"));
        }
        Optional<Path> cached = findCached(major);
        if (cached.isPresent()) {
            return cached;
        }
        for (Path c : systemCandidates(major)) {
            if (Files.isRegularFile(c) && installedMajor(c.toString()).orElse(-1) == major) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public static Path install(int major, Reporter rep) throws Exception {
        Optional<Path> existing = resolve(major, null);
        if (existing.isPresent()) {
            rep.log("Using existing Java");
            return existing.get();
        }
        Path dest = cacheRoot().resolve(String.valueOf(major));
        Files.createDirectories(dest);
        if (findJava(dest).filter(p -> installedMajor(p.toString()).orElse(-1) == major).isPresent()) {
            rep.log("Using cached Java");
            return findJava(dest).get();
        }

        String arch = switch (System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> System.getProperty("os.arch");
        };
        String os;
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            os = "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            os = "mac";
        } else {
            os = "linux";
        }
        Path archive = dest.resolve(osName.contains("win") ? "pkg.zip" : "pkg.tar.gz");
        boolean downloaded = false;
        for (String image : List.of("jre", "jdk")) {
            String url = "https://api.adoptium.net/v3/binary/latest/" + major
                    + "/ga/" + os + "/" + arch + "/" + image + "/hotspot/normal/eclipse";
            rep.log("Downloading Java " + major + " (" + image + ")...");
            try {
                sparkmc.net.Http.download(url, archive);
                downloaded = true;
                break;
            } catch (Exception e) {
                rep.log("  " + image + " unavailable: " + e.getMessage());
            }
        }
        if (!downloaded) {
            throw new IOException("no Temurin Java " + major + " build for " + os + "/" + arch);
        }
        rep.log("Extracting...");
        extract(archive, dest);
        Files.deleteIfExists(archive);
        return findJava(dest).orElseThrow(() -> new IOException("java executable not found after extraction"));
    }

    public static Optional<Integer> neededMajorFromLine(String line) {
        String marker = "class file version ";
        int idx = line.indexOf(marker);
        if (idx < 0) {
            return Optional.empty();
        }
        String rest = line.substring(idx + marker.length());
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.isEmpty()) {
            return Optional.empty();
        }
        try {
            int classVersion = Integer.parseInt(digits.toString());
            return Optional.of(Math.max(0, classVersion - 44));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseMajor(String text) {
        int idx = text.indexOf("version \"");
        if (idx < 0) {
            return Optional.empty();
        }
        String rest = text.substring(idx + 9);
        int end = rest.indexOf('"');
        if (end < 0) {
            return Optional.empty();
        }
        String version = rest.substring(0, end);
        String[] parts = version.split("\\.");
        try {
            if ("1".equals(parts[0]) && parts.length > 1) {
                return Optional.of(Integer.parseInt(parts[1]));
            }
            String first = parts[0].replaceAll("\\D.*", "");
            return Optional.of(Integer.parseInt(first));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Path cacheRoot() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String pf = System.getenv("ProgramFiles");
            if (pf == null || pf.isBlank()) {
                pf = "C:\\Program Files";
            }
            return Path.of(pf, "Java");
        }
        String home = System.getProperty("user.home");
        return Path.of(home, ".cache", "sparkmc", "java");
    }

    private static Optional<Path> findCached(int major) {
        Path dest = cacheRoot().resolve(String.valueOf(major));
        return findJava(dest).filter(p -> installedMajor(p.toString()).orElse(-1) == major);
    }

    private static List<Path> systemCandidates(int major) {
        List<Path> out = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String pf = Optional.ofNullable(System.getenv("ProgramFiles")).orElse("C:\\Program Files");
            out.add(Path.of(pf, "Java", "jdk-" + major, "bin", "java.exe"));
            out.add(Path.of(pf, "Java", "jre-" + major, "bin", "java.exe"));
            out.add(Path.of(pf, "Eclipse Adoptium", "jdk-" + major, "bin", "java.exe"));
            out.add(Path.of(pf, "Microsoft", "jdk-" + major, "bin", "java.exe"));
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome != null) {
                out.add(Path.of(javaHome, "bin", "java.exe"));
            }
        } else {
            out.add(Path.of("/usr/lib/jvm/java-" + major + "-openjdk/bin/java"));
            out.add(Path.of("/usr/lib/jvm/java-" + major + "-temurin/bin/java"));
            out.add(Path.of("/usr/lib/jvm/temurin-" + major + "-jdk/bin/java"));
            out.add(Path.of("/usr/lib/jvm/jdk-" + major + "/bin/java"));
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome != null) {
                out.add(Path.of(javaHome, "bin", "java"));
            }
        }
        return out;
    }

    private static Optional<Path> findJava(Path root) {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        String exe = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        final Path[] found = {null};
        final Path[] fallback = {null};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(exe)) {
                        Path parent = file.getParent();
                        if (parent != null && "bin".equals(parent.getFileName().toString())) {
                            found[0] = file;
                            return FileVisitResult.TERMINATE;
                        }
                        if (fallback[0] == null) {
                            fallback[0] = file;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
        return Optional.ofNullable(found[0] != null ? found[0] : fallback[0]);
    }

    private static void extract(Path archive, Path dest) throws Exception {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            try (InputStream in = Files.newInputStream(archive); ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    Path out = dest.resolve(e.getName()).normalize();
                    if (!out.startsWith(dest)) {
                        throw new IOException("zip slip: " + e.getName());
                    }
                    if (e.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        Files.copy(zis, out);
                    }
                }
            }
            return;
        }
        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", dest.toString());
        pb.inheritIO();
        int code = pb.start().waitFor();
        if (code != 0) {
            throw new IOException("extraction failed");
        }
    }
}
