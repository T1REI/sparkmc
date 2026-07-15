package sparkmc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        for (Path c : systemCandidates(major)) {
            if (Files.isRegularFile(c) && installedMajor(c.toString()).orElse(-1) == major) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
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
}
