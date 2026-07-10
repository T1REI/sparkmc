package sparkmc.model;

public final class ServerConfig {
    private final Core core;
    private final String version;
    private final LoaderChannel channel;
    private final FlagPreset preset;
    private final String customFlags;
    private final int ramMb;
    private final boolean noGui;
    private final boolean autoRestart;

    public ServerConfig(
            Core core,
            String version,
            LoaderChannel channel,
            FlagPreset preset,
            String customFlags,
            int ramMb,
            boolean noGui,
            boolean autoRestart) {
        this.core = core;
        this.version = version;
        this.channel = channel;
        this.preset = preset;
        this.customFlags = customFlags == null ? "" : customFlags;
        this.ramMb = ramMb;
        this.noGui = noGui;
        this.autoRestart = autoRestart;
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

    public FlagPreset preset() {
        return preset;
    }

    public String customFlags() {
        return customFlags;
    }

    public int ramMb() {
        return ramMb;
    }

    public boolean noGui() {
        return noGui;
    }

    public boolean autoRestart() {
        return autoRestart;
    }
}
