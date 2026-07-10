package sparkmc.net;

import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.util.Reporter;
import sparkmc.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class NeoForgeProvider implements Provider {
    private static final String VERSIONS =
            "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";
    private static final String MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge";

    @Override
    public List<String> versions() throws Exception {
        VersionList list = Http.getJson(VERSIONS, VersionList.class);
        Map<String, List<String>> map = buildsPerMc(list.versions);
        List<String> mc = new ArrayList<>(map.keySet());
        mc.sort((a, b) -> Util.cmpVersion(b, a));
        return mc;
    }

    @Override
    public LaunchTarget prepare(String version, LoaderChannel channel, Path dir, Reporter rep)
            throws Exception {
        VersionList list = Http.getJson(VERSIONS, VersionList.class);
        LoaderChannel ch = channel == null ? LoaderChannel.Recommended : channel;
        Map<String, List<String>> map = buildsPerMc(list.versions);
        List<String> builds = map.get(version);
        if (builds == null || builds.isEmpty()) {
            throw new IllegalStateException("no neoforge build for " + version);
        }
        String neoforge = pickBuild(builds, ch);
        if (neoforge == null) {
            throw new IllegalStateException("no neoforge build for " + version + " (" + ch.label() + ")");
        }
        rep.log("Using NeoForge " + neoforge + " (" + ch.label() + ")");
        Path installer = dir.resolve("neoforge-" + neoforge + "-installer.jar");
        String url = MAVEN + "/" + neoforge + "/neoforge-" + neoforge + "-installer.jar";
        NetUtil.downloadLogged(url, installer, rep);
        NetUtil.runInstaller(dir, installer, rep);
        NetUtil.removeIfExists(installer);
        NetUtil.removeIfExists(dir.resolve("neoforge-" + neoforge + "-installer.jar.log"));
        NetUtil.cleanupInstallerJunk(dir, rep);
        Path args = NetUtil.findFile(dir, NetUtil.argfileName());
        if (args == null) {
            throw new IllegalStateException("could not locate neoforge args file after install");
        }
        return LaunchTarget.argFile(args);
    }

    private static String neoforgeToMc(String nf) {
        String[] parts = nf.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        if (!parts[0].chars().allMatch(Character::isDigit) || !parts[1].chars().allMatch(Character::isDigit)) {
            return null;
        }
        if ("0".equals(parts[1])) {
            return "1." + parts[0];
        }
        return "1." + parts[0] + "." + parts[1];
    }

    private static boolean isPrerelease(String nf) {
        String lower = nf.toLowerCase(Locale.ROOT);
        return lower.contains("beta") || lower.contains("alpha") || lower.contains("rc");
    }

    private static Map<String, List<String>> buildsPerMc(List<String> versions) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String nf : versions) {
            String mc = neoforgeToMc(nf);
            if (mc == null) {
                continue;
            }
            map.computeIfAbsent(mc, k -> new ArrayList<>()).add(nf);
        }
        for (List<String> builds : map.values()) {
            builds.sort(Util::cmpVersion);
        }
        return map;
    }

    private static String pickBuild(List<String> builds, LoaderChannel channel) {
        if (builds.isEmpty()) {
            return null;
        }
        if (channel == LoaderChannel.Latest) {
            return builds.get(builds.size() - 1);
        }
        for (int i = builds.size() - 1; i >= 0; i--) {
            if (!isPrerelease(builds.get(i))) {
                return builds.get(i);
            }
        }
        return builds.get(builds.size() - 1);
    }

    private static final class VersionList {
        List<String> versions;
    }
}
