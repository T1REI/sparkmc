package sparkmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import sparkmc.model.Core;
import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.model.ServerConfig;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LaunchPlan {
    private static final String FILE = "sparkmc.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Core core = Core.Vanilla;
    public String version = "";
    public LoaderChannel channel;
    public String target = "";
    public Integer required_java;
    public String java;

    // legacy optional fields
    public String title;
    public String program;
    public List<String> args;

    public static LaunchPlan fromConfig(ServerConfig cfg, LaunchTarget target, Integer requiredJava, Path dir) {
        LaunchPlan plan = new LaunchPlan();
        plan.core = cfg.core();
        plan.version = cfg.version();
        plan.channel = cfg.channel();
        plan.target = toTargetString(target, dir);
        plan.required_java = requiredJava;
        return plan;
    }

    public String title() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return "sparkmc - " + core.label() + " " + version;
    }

    public String program() {
        if (program != null && !program.isBlank()) {
            return program;
        }
        String home = System.getProperty("java.home");
        if (home != null && !home.isBlank()) {
            boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path java = Path.of(home, "bin", win ? "java.exe" : "java");
            if (Files.isRegularFile(java)) {
                return java.toString();
            }
        }
        return "java";
    }

    public List<String> args(List<String> extraArgs) {
        List<String> jvm = new ArrayList<>(ManagementFactory.getRuntimeMXBean().getInputArguments());
        List<String> server = new ArrayList<>();
        if (extraArgs != null) {
            for (String a : extraArgs) {
                if (isJvmFlag(a)) {
                    jvm.add(a);
                } else {
                    server.add(a);
                }
            }
        }

        List<String> out = new ArrayList<>(jvm);
        String t = target == null ? "" : target.trim();
        if (t.startsWith("@")) {
            out.add(t);
        } else if (!t.isEmpty()) {
            out.add("-jar");
            out.add(t);
        } else if (args != null && !args.isEmpty()) {
            out = new ArrayList<>(args);
        }
        out.addAll(server);
        return out;
    }

    private static boolean isJvmFlag(String arg) {
        return arg.startsWith("-X")
                || arg.startsWith("-D")
                || arg.startsWith("-XX")
                || arg.startsWith("-ea")
                || arg.startsWith("-da")
                || arg.startsWith("-server")
                || arg.startsWith("-client")
                || arg.startsWith("-javaagent:")
                || arg.startsWith("-agent")
                || arg.startsWith("-verbose")
                || arg.startsWith("--add-")
                || arg.startsWith("--enable-")
                || arg.startsWith("--patch-module")
                || arg.startsWith("--module-path")
                || arg.startsWith("--upgrade-module-path")
                || arg.startsWith("--limit-modules")
                || arg.startsWith("--illegal-access");
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
        if (plan.core == null) {
            plan.core = Core.Vanilla;
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
