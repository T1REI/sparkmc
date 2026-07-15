package sparkmc;

import sparkmc.net.NetUtil;
import sparkmc.util.Ansi;
import sparkmc.util.Util;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/** Checks GitHub releases on start and offers to self-update. Any failure is silent. */
public final class Updater {
    private static final String LATEST =
            "https://api.github.com/repos/T1REI/sparkmc/releases/latest";
    private static final String ASSET = "sparkmc.jar";

    private Updater() {}

    private static final class Release {
        String tag_name;
        List<Asset> assets;
    }

    private static final class Asset {
        String name;
        String browser_download_url;
    }

    public static void checkAndOffer() {
        Path jar = selfJar();
        if (jar == null) {
            return; // not running from a jar (IDE / classes), nothing to update
        }
        Release release;
        try {
            release = NetUtil.getJson(LATEST, Release.class);
        } catch (Exception e) {
            return; // offline / no releases yet - not our problem right now
        }
        if (release == null || release.tag_name == null || release.assets == null) {
            return;
        }
        String latest = release.tag_name.startsWith("v") ? release.tag_name.substring(1) : release.tag_name;
        if (Util.cmpVersion(latest, NetUtil.version()) <= 0) {
            return;
        }
        String url = null;
        for (Asset a : release.assets) {
            if (ASSET.equals(a.name)) {
                url = a.browser_download_url;
                break;
            }
        }
        if (url == null) {
            return;
        }

        System.out.println(Ansi.YELLOW + "[sparkmc]" + Ansi.RESET + " update " + Ansi.WHITE + latest
                + Ansi.RESET + " is available (you have " + NetUtil.version() + ")");
        System.out.println(Ansi.YELLOW + "[sparkmc]" + Ansi.RESET + " update now? " + Ansi.GRAY + "[Y/n]" + Ansi.RESET);
        System.out.print(Ansi.GREEN + ">" + Ansi.RESET + " ");
        System.out.flush();
        String answer;
        try {
            answer = readLineUnbuffered();
        } catch (Exception e) {
            return;
        }
        if (answer != null) {
            String a = answer.trim().toLowerCase(Locale.ROOT);
            if (!(a.isEmpty() || a.startsWith("y") || a.startsWith("д"))) {
                System.out.println(Ansi.GRAY + "[sparkmc] skipped, continuing with " + NetUtil.version() + Ansi.RESET);
                return;
            }
        }

        try {
            Path fresh = jar.resolveSibling(jar.getFileName() + ".new");
            System.out.println(Ansi.GREEN + "[sparkmc]" + Ansi.RESET + " downloading " + latest + "...");
            NetUtil.download(url, fresh);
            try {
                Files.move(fresh, jar, StandardCopyOption.REPLACE_EXISTING);
                System.out.println(Ansi.GREEN + "[sparkmc]" + Ansi.RESET
                        + " updated to " + latest + ", restart to apply");
            } catch (Exception locked) {
                // Windows keeps the running jar locked - swap it after the JVM exits.
                scheduleSwapOnExit(fresh, jar);
                System.out.println(Ansi.GREEN + "[sparkmc]" + Ansi.RESET
                        + " update downloaded, it will be applied after you close sparkmc");
            }
        } catch (Exception e) {
            System.out.println(Ansi.RED + "[sparkmc]" + Ansi.RESET + " update failed: " + e.getMessage());
        }
    }

    /**
     * Reads one line from System.in byte-by-byte, without buffering ahead:
     * the wizard/console has its own reader and must see the rest of stdin.
     */
    private static String readLineUnbuffered() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = System.in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            out.write(b);
        }
        if (b == -1 && out.size() == 0) {
            return null;
        }
        String line = out.toString(StandardCharsets.UTF_8);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static Path selfJar() {
        try {
            Path path = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return path;
            }
            return null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static void scheduleSwapOnExit(Path fresh, Path jar) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();
                String script = "@echo off\r\n"
                        + ":wait\r\n"
                        + "tasklist /FI \"PID eq " + pid + "\" 2>nul | find \"" + pid + "\" >nul && (\r\n"
                        + "  timeout /t 1 /nobreak >nul\r\n"
                        + "  goto wait\r\n"
                        + ")\r\n"
                        + "move /y \"" + fresh + "\" \"" + jar + "\" >nul\r\n"
                        + "del \"%~f0\"\r\n";
                Path bat = jar.resolveSibling("sparkmc-update.bat");
                Files.writeString(bat, script);
                new ProcessBuilder("cmd", "/c", "start", "/min", "\"\"", bat.toString())
                        .directory(jar.getParent().toFile())
                        .start();
            } catch (Exception ignored) {
            }
        }, "sparkmc-update"));
    }
}
