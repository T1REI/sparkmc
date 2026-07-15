package sparkmc.model;

public enum Core {
    Vanilla,
    Forge,
    Fabric,
    NeoForge,
    Paper,
    Purpur;

    public String label() {
        return name();
    }

    public String contentFolder() {
        return switch (this) {
            case Paper, Purpur -> "plugins";
            case Forge, Fabric, NeoForge -> "mods";
            case Vanilla -> null;
        };
    }

    public boolean supportsChannel() {
        return this == Forge || this == NeoForge;
    }

    public static Core[] all() {
        return values();
    }
}
