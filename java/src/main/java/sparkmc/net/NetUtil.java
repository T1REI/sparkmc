package sparkmc.net;

import sparkmc.util.Reporter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class NetUtil {
    private NetUtil() {}

    static void downloadLogged(String url, Path path, Reporter rep) throws IOException {
        rep.log("Downloading " + url);
        long bytes = Http.download(url, path);
        rep.log("Saved " + path + " (" + (bytes / 1024) + " KiB)");
    }

    static void runInstaller(Path dir, Path installer, Reporter rep) throws IOException, InterruptedException {
        rep.log("Running installer (--installServer), this may take a while...");
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", installer.toString(), "--installServer");
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        boolean finished = p.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("installer timed out");
        }
        if (p.exitValue() != 0) {
            throw new IOException("installer exited with error code " + p.exitValue());
        }
        rep.log("Installer finished");
    }

    static Path findFile(Path root, String name) throws IOException {
        final Path[] found = {null};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals(name)) {
                    found[0] = file;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found[0];
    }

    static String argfileName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "win_args.txt" : "unix_args.txt";
    }

    static void removeIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    static void cleanupInstallerJunk(Path dir, Reporter rep) throws IOException {
        String[] exact = {
            "installer.log",
            "install.log",
            "run.bat",
            "run.sh",
            "user_jvm_args.txt",
            "startserver.bat",
            "startserver.sh",
            "start.bat",
            "start.sh",
            "readme.txt",
            "README.txt",
            "forge-installer.jar",
            "neoforge-installer.jar",
        };
        for (String name : exact) {
            Path p = dir.resolve(name);
            if (Files.isRegularFile(p) && Files.deleteIfExists(p)) {
                rep.log("Removed " + name);
            }
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                boolean junk = lower.endsWith("-installer.jar")
                        || lower.endsWith("-installer.jar.log")
                        || lower.endsWith("installer.jar.log")
                        || lower.endsWith(".jar.log")
                        || lower.endsWith("-installer.log")
                        || (lower.startsWith("forge-") && lower.endsWith(".log"))
                        || (lower.startsWith("neoforge-") && lower.endsWith(".log"));
                if (junk) {
                    try {
                        if (Files.deleteIfExists(path)) {
                            rep.log("Removed " + name);
                        }
                    } catch (IOException ignored) {
                    }
                }
            });
        }
    }
}
