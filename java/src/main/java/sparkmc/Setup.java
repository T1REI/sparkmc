package sparkmc;

import sparkmc.model.LaunchTarget;
import sparkmc.model.ServerConfig;
import sparkmc.net.Providers;
import sparkmc.net.RequiredJava;
import sparkmc.util.Reporter;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Setup {
    private static final String EULA =
            "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\n"
                    + "#sparkmc\n"
                    + "eula=true\n";

    private Setup() {}

    public static LaunchPlan run(Path dir, ServerConfig cfg, Reporter rep) throws Exception {
        rep.log("Accepting EULA (eula.txt)");
        Files.writeString(dir.resolve("eula.txt"), EULA);

        String channelNote = cfg.channel() == null ? "" : " [" + cfg.channel().label() + "]";
        rep.log("Preparing " + cfg.core().label() + " " + cfg.version() + channelNote);
        LaunchTarget target = Providers.of(cfg.core()).prepare(cfg.version(), cfg.channel(), dir, rep);

        String folder = cfg.core().contentFolder();
        if (folder != null) {
            Path path = dir.resolve(folder);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                rep.log("Created " + folder + "/");
            }
        }

        Integer requiredJava = RequiredJava.forMcVersion(cfg.version());
        if (requiredJava != null) {
            rep.log("Server requires Java " + requiredJava);
        }
        rep.log("Setup complete");
        return LaunchPlan.fromConfig(cfg, target, requiredJava, dir);
    }
}
