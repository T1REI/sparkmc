package sparkmc.net;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import sparkmc.util.Reporter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class NetUtil {
    private static final Gson GSON = new Gson();
    private static final String UA = "sparkmc/" + version() + " (https://github.com/T1REI/sparkmc)";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NetUtil() {}

    public static String version() {
        String v = NetUtil.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? "0.0.2" : v;
    }

    public static <T> T getJson(String url, Class<T> type) throws IOException {
        String body = getString(url);
        return GSON.fromJson(body, type);
    }

    public static <T> T getJson(String url, Type type) throws IOException {
        String body = getString(url);
        return GSON.fromJson(body, type);
    }

    public static String getString(String url) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IOException("request failed (" + url + "): status code " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    public static long download(String url, Path path) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(30))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                throw new IOException("download failed (" + url + "): status code " + resp.statusCode());
            }
            Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
            try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(path)) {
                return in.transferTo(out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    public static Type listOf(Class<?> element) {
        return TypeToken.getParameterized(java.util.List.class, element).getType();
    }

    static void downloadLogged(String url, Path path, Reporter rep) throws IOException {
        rep.log("Downloading " + url);
        long bytes = download(url, path);
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
