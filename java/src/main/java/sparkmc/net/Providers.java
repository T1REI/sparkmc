package sparkmc.net;

import sparkmc.model.Core;

public final class Providers {
    private Providers() {}

    public static Provider of(Core core) {
        return switch (core) {
            case Vanilla -> new VanillaProvider();
            case Forge -> new ForgeProvider();
            case Fabric -> new FabricProvider();
            case NeoForge -> new NeoForgeProvider();
            case Paper -> new PaperProvider();
            case Purpur -> new PurpurProvider();
        };
    }
}
