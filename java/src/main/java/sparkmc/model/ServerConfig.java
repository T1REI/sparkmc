package sparkmc.model;

public final class ServerConfig {
    private final Core core;
    private final String version;
    private final LoaderChannel channel;

    public ServerConfig(Core core, String version, LoaderChannel channel) {
        this.core = core;
        this.version = version;
        this.channel = channel;
    }

    public Core core() {
        return core;
    }

    public String version() {
        return version;
    }

    public LoaderChannel channel() {
        return channel;
    }
}
