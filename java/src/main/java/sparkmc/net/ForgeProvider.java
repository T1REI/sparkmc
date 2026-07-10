package sparkmc.net;

import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.util.Reporter;
import sparkmc.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ForgeProvider implements Provider {
    private static final String PROMOS =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge";

    @Override
    public List<String> versions() throws Exception {
        Promotions data = Http.getJson(PROMOS, Promotions.class);
        Set<String> set = new LinkedHashSet<>();
        for (String key : data.promos.keySet()) {
            int idx = key.lastIndexOf('-');
            if (idx > 0) {
                set.add(key.substring(0, idx));
            }
        }
        List<String> mc = new ArrayList<>(set);
        mc.sort((a, b) -> Util.cmpVersion(b, a));
        return mc;
    }

    @Override
    public LaunchTarget prepare(String version, LoaderChannel channel, Path dir, Reporter rep)
            throws Exception {
        Promotions data = Http.getJson(PROMOS, Promotions.class);
        LoaderChannel ch = channel == null ? LoaderChannel.Recommended : channel;
        String forge = resolvePromo(data.promos, version, ch);
        if (forge == null) {
            throw new IllegalStateException("no forge build for " + version + " (" + ch.label() + ")");
        }
        String full = version + "-" + forge;
        Path installer = dir.resolve("forge-" + full + "-installer.jar");
        String url = MAVEN + "/" + full + "/forge-" + full + "-installer.jar";
        NetUtil.downloadLogged(url, installer, rep);
        NetUtil.runInstaller(dir, installer, rep);
        NetUtil.removeIfExists(installer);
        NetUtil.removeIfExists(dir.resolve("forge-" + full + "-installer.jar.log"));
        NetUtil.cleanupInstallerJunk(dir, rep);

        Path args = NetUtil.findFile(dir, NetUtil.argfileName());
        if (args != null) {
            rep.log("Using generated args file");
            return LaunchTarget.argFile(args);
        }
        Path legacy = dir.resolve("forge-" + full + ".jar");
        if (Files.isRegularFile(legacy)) {
            return LaunchTarget.jar(legacy);
        }
        Path universal = dir.resolve("forge-" + full + "-universal.jar");
        if (Files.isRegularFile(universal)) {
            return LaunchTarget.jar(universal);
        }
        throw new IllegalStateException("could not locate forge launch target after install");
    }

    private static String resolvePromo(Map<String, String> promos, String version, LoaderChannel channel) {
        String primary = version + "-" + channel.key();
        String fallback = channel == LoaderChannel.Recommended
                ? version + "-latest"
                : version + "-recommended";
        if (promos.containsKey(primary)) {
            return promos.get(primary);
        }
        return promos.get(fallback);
    }

    private static final class Promotions {
        Map<String, String> promos;
    }
}
