package sparkmc.model;

import java.nio.file.Path;

public final class LaunchTarget {
    public enum Kind {
        JAR,
        ARG_FILE
    }

    private final Kind kind;
    private final Path path;

    private LaunchTarget(Kind kind, Path path) {
        this.kind = kind;
        this.path = path;
    }

    public static LaunchTarget jar(Path path) {
        return new LaunchTarget(Kind.JAR, path);
    }

    public static LaunchTarget argFile(Path path) {
        return new LaunchTarget(Kind.ARG_FILE, path);
    }

    public Kind kind() {
        return kind;
    }

    public Path path() {
        return path;
    }
}
