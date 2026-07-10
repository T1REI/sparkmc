package sparkmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import sparkmc.model.Core;
import sparkmc.model.FlagPreset;
import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.model.Prepared;
import sparkmc.model.ServerConfig;
import sparkmc.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LaunchPlan {
    private static final String FILE = "sparkmc.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Core core = Core.Vanilla;
    public String version = "";
    public LoaderChannel channel;
    public FlagPreset flags = FlagPreset.Aikar;
    public String custom_flags = "";
    public int ram_mb = 4096;
    public boolean no_gui = true;
    public boolean auto_restart = true;
    public String target = "";
    public Integer required_java;
    public String java;

    // legacy optional fields
    public String title;
    public String program;
    public List<String> args;

    public static LaunchPlan fromConfig(ServerConfig cfg, Prepared prepared, Path dir) {
        LaunchPlan plan = new LaunchPlan();
        plan.core = cfg.core();
        plan.version = cfg.version();
        plan.channel = cfg.channel();
        plan.flags = cfg.preset();
        plan.custom_flags = cfg.customFlags();
        plan.ram_mb = cfg.ramMb();
        plan.no_gui = cfg.noGui();
        plan.auto_restart = cfg.autoRestart();
        plan.required_java = prepared.requiredJava();
        plan.target = toTargetString(prepared.target(), dir);
        return plan;
    }

    public String title() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return "sparkmc - " + core.label() + " " + version;
    }

    public String program() {
        if (java != null && !java.isBlank()) {
            return java;
        }
        if (program != null && !program.isBlank()) {
            return program;
        }
        return "java";
    }

    public List<String> args() {
        long heap = Math.max(Util.heapMb(Math.max(ram_mb, 1024)), Util.MIN_HEAP_MB);
        List<String> out = new ArrayList<>();
        for (String f : flags.jvmFlags()) {
            out.add(f);
        }
        out.add("-Xms" + heap + "M");
        out.add("-Xmx" + heap + "M");
        if (flags == FlagPreset.Custom && custom_flags != null) {
            for (String p : custom_flags.trim().split("\\s+")) {
                if (!p.isBlank()) {
                    out.add(p);
                }
            }
        }
        String t = target == null ? "" : target.trim();
        if (t.startsWith("@")) {
            out.add(t);
        } else if (!t.isEmpty()) {
            out.add("-jar");
            out.add(t);
        } else if (args != null && !args.isEmpty()) {
            return new ArrayList<>(args);
        }
        if (no_gui) {
            out.add(core.noguiArg());
        }
        return out;
    }

    public void save(Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), GSON.toJson(this));
    }

    public static LaunchPlan load(Path dir) throws IOException {
        String json = Files.readString(dir.resolve(FILE));
        LaunchPlan plan = GSON.fromJson(json, LaunchPlan.class);
        if (plan == null) {
            throw new IOException("invalid " + FILE);
        }
        if ((plan.target == null || plan.target.isBlank()) && plan.args != null) {
            plan.target = extractTarget(plan.args);
        }
        if (plan.ram_mb == 0) {
            plan.ram_mb = 4096;
        }
        if (plan.core == null) {
            plan.core = Core.Vanilla;
        }
        if (plan.flags == null) {
            plan.flags = FlagPreset.Aikar;
        }
        return plan;
    }

    public static boolean exists(Path dir) {
        return Files.isRegularFile(dir.resolve(FILE));
    }

    private static String toTargetString(LaunchTarget target, Path dir) {
        Path rel;
        try {
            rel = dir.toAbsolutePath().relativize(target.path().toAbsolutePath());
        } catch (Exception e) {
            rel = target.path();
        }
        String s = rel.toString();
        if (target.kind() == LaunchTarget.Kind.ARG_FILE) {
            return "@" + s;
        }
        return s;
    }

    private static String extractTarget(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            if ("-jar".equals(args.get(i)) && i + 1 < args.size()) {
                return args.get(i + 1);
            }
            if (args.get(i).startsWith("@")) {
                return args.get(i);
            }
        }
        return "";
    }
}
