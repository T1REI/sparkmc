package sparkmc;

import sparkmc.model.Core;
import sparkmc.model.FlagPreset;
import sparkmc.model.LoaderChannel;
import sparkmc.model.Prepared;
import sparkmc.model.ServerConfig;
import sparkmc.net.Providers;
import sparkmc.util.Ansi;
import sparkmc.util.Reporter;
import sparkmc.util.Util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class Wizard {
    private static final BufferedReader IN =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private Wizard() {}

    public static void run(Path dir) {
        banner();
        Core core = pickCore();
        if (core == null) {
            return;
        }
        List<String> versions;
        try {
            info("Loading " + core.label() + " versions...");
            versions = Providers.of(core).versions();
        } catch (Exception e) {
            err(e.getMessage());
            waitEnter();
            return;
        }
        if (versions.isEmpty()) {
            err("no versions returned");
            waitEnter();
            return;
        }
        String version = pickVersion(versions);
        if (version == null) {
            return;
        }
        LoaderChannel channel = null;
        if (core.supportsChannel()) {
            channel = pickChannel();
            if (channel == null) {
                return;
            }
        }
        FlagPreset preset = pickFlags();
        if (preset == null) {
            return;
        }
        String customFlags = "";
        if (preset == FlagPreset.Custom) {
            customFlags = readLine("Custom JVM flags");
            if (customFlags == null || customFlags.isBlank()) {
                err("custom flags cannot be empty");
                waitEnter();
                return;
            }
        }
        Integer ram = pickRam();
        if (ram == null) {
            return;
        }
        boolean noGui = confirm("No GUI (--nogui)?", true);
        boolean autoRestart = confirm("Auto restart on crash?", true);

        ServerConfig cfg = new ServerConfig(core, version, channel, preset, customFlags, ram, noGui, autoRestart);
        System.out.println();
        info("Review");
        row("core", cfg.core().label());
        row("version", cfg.version());
        if (cfg.channel() != null) {
            row("channel", cfg.channel().label());
        }
        row("flags", cfg.preset().label());
        row("ram", cfg.ramMb() + " MB");
        row("nogui", cfg.noGui() ? "yes" : "no");
        row("restart", cfg.autoRestart() ? "yes" : "no");
        System.out.println();
        if (!confirm("Launch setup now?", true)) {
            info("Cancelled");
            return;
        }

        Reporter rep = new Reporter();
        try {
            Prepared prepared = Setup.run(dir, cfg, rep);
            LaunchPlan plan = LaunchPlan.fromConfig(cfg, prepared, dir);
            plan.save(dir);
            info("Configuration saved. Starting server console...");
            ConsoleApp.run(dir);
        } catch (Exception e) {
            err(e.getMessage());
            waitEnter();
        }
    }

    private static void banner() {
        System.out.println(Ansi.GREEN + "sparkmc" + Ansi.RESET + Ansi.GRAY + " - Minecraft server setup" + Ansi.RESET);
        System.out.println(Ansi.GRAY + "Console wizard. Ctrl+C to abort." + Ansi.RESET);
        System.out.println();
    }

    private static Core pickCore() {
        Core[] items = Core.all();
        String[] labels = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            labels[i] = items[i].label();
        }
        Integer idx = pickIndex("Select core", labels, 0);
        return idx == null ? null : items[idx];
    }

    private static LoaderChannel pickChannel() {
        LoaderChannel[] items = LoaderChannel.all();
        String[] labels = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            labels[i] = items[i].label();
        }
        Integer idx = pickIndex("Forge/NeoForge channel", labels, 0);
        return idx == null ? null : items[idx];
    }

    private static FlagPreset pickFlags() {
        FlagPreset[] items = FlagPreset.all();
        String[] labels = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            labels[i] = items[i].label();
        }
        Integer idx = pickIndex("JVM flags preset", labels, 0);
        return idx == null ? null : items[idx];
    }

    private static String pickVersion(List<String> versions) {
        String def = versions.get(0);
        System.out.println(Ansi.CYAN + "Versions:" + Ansi.RESET + " " + Ansi.WHITE + String.join(", ", versions) + Ansi.RESET);
        System.out.println(Ansi.GRAY + "Type the version itself, e.g. 1.12.2 (not a number)." + Ansi.RESET);
        while (true) {
            System.out.println(Ansi.YELLOW + "[sparkmc]" + Ansi.RESET + " " + Ansi.WHITE + "version" + Ansi.RESET
                    + " " + Ansi.GRAY + "[" + def + "]" + Ansi.RESET);
            prompt();
            String input = readRaw();
            if (input == null) {
                return null;
            }
            String chosen = input.isBlank() ? def : input.trim();
            String resolved = resolveVersion(versions, chosen);
            if (resolved != null) {
                info("selected " + resolved);
                return resolved;
            }
            err("unknown version '" + chosen + "'. Example: 1.12.2");
        }
    }

    private static String resolveVersion(List<String> versions, String input) {
        for (String v : versions) {
            if (v.equals(input)) {
                return v;
            }
        }
        String lower = input.toLowerCase(Locale.ROOT);
        String exact = null;
        for (String v : versions) {
            if (v.toLowerCase(Locale.ROOT).equals(lower)) {
                if (exact != null) {
                    return null;
                }
                exact = v;
            }
        }
        if (exact != null) {
            return exact;
        }
        String prefix = null;
        for (String v : versions) {
            if (v.toLowerCase(Locale.ROOT).startsWith(lower)) {
                if (prefix != null) {
                    return null;
                }
                prefix = v;
            }
        }
        return prefix;
    }

    private static Integer pickRam() {
        while (true) {
            System.out.println(Ansi.YELLOW + "[sparkmc]" + Ansi.RESET + " " + Ansi.WHITE + "RAM in MB "
                    + Ansi.GRAY + "[4096]" + Ansi.RESET);
            prompt();
            String input = readRaw();
            if (input == null) {
                return null;
            }
            int value;
            if (input.isBlank()) {
                value = 4096;
            } else {
                try {
                    value = Integer.parseInt(input.trim());
                } catch (NumberFormatException e) {
                    err("enter a number between 1024 and 65536");
                    continue;
                }
                if (value < 1024 || value > 65536) {
                    err("enter a number between 1024 and 65536");
                    continue;
                }
            }
            long heap = Util.heapMb(value);
            if (heap < Util.MIN_HEAP_MB) {
                err("heap would be " + heap + " MB (min " + Util.MIN_HEAP_MB + "), pick more RAM");
                continue;
            }
            info("heap ≈ " + heap + " MB");
            return value;
        }
    }

    private static Integer pickIndex(String title, String[] labels, int def) {
        System.out.println(Ansi.CYAN + title + Ansi.RESET);
        for (int i = 0; i < labels.length; i++) {
            String mark = i == def ? ">" : " ";
            System.out.printf("  %s%s%s %s%2d. %s%s%n", Ansi.GRAY, mark, Ansi.RESET, Ansi.WHITE, i + 1, labels[i], Ansi.RESET);
        }
        while (true) {
            System.out.println(Ansi.YELLOW + "[sparkmc]" + Ansi.RESET + " choose 1-" + labels.length + " "
                    + Ansi.GRAY + "[" + (def + 1) + "]" + Ansi.RESET);
            prompt();
            String input = readRaw();
            if (input == null) {
                return null;
            }
            if (input.isBlank()) {
                return def;
            }
            try {
                int n = Integer.parseInt(input.trim());
                if (n >= 1 && n <= labels.length) {
                    return n - 1;
                }
            } catch (NumberFormatException ignored) {
            }
            err("enter a number from 1 to " + labels.length);
        }
    }

    private static boolean confirm(String promptText, boolean defaultYes) {
        String hint = defaultYes ? "Y/n" : "y/N";
        System.out.println(Ansi.YELLOW + "[sparkmc]" + Ansi.RESET + " " + Ansi.WHITE + promptText + Ansi.RESET
                + " " + Ansi.GRAY + "[" + hint + "]" + Ansi.RESET);
        prompt();
        String input = readRaw();
        if (input == null) {
            return defaultYes;
        }
        if (input.isBlank()) {
            return defaultYes;
        }
        String a = input.trim().toLowerCase(Locale.ROOT);
        return a.startsWith("y") || a.startsWith("д");
    }

    private static String readLine(String promptText) {
        System.out.println(Ansi.YELLOW + "[sparkmc]" + Ansi.RESET + " " + Ansi.WHITE + promptText + Ansi.RESET);
        prompt();
        return readRaw();
    }

    private static void waitEnter() {
        System.out.println(Ansi.GRAY + "press Enter to close" + Ansi.RESET);
        prompt();
        readRaw();
    }

    private static void prompt() {
        System.out.print(Ansi.GREEN + ">" + Ansi.RESET + " ");
        System.out.flush();
    }

    private static String readRaw() {
        try {
            return IN.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private static void row(String key, String value) {
        System.out.printf("  %s%-10s%s %s%s%s%n", Ansi.GRAY, key, Ansi.RESET, Ansi.WHITE, value, Ansi.RESET);
    }

    private static void info(String msg) {
        System.out.println(Ansi.GREEN + "[sparkmc]" + Ansi.RESET + " " + Ansi.WHITE + msg + Ansi.RESET);
    }

    private static void err(String msg) {
        System.out.println(Ansi.RED + "[sparkmc]" + Ansi.RESET + " " + msg);
    }
}
