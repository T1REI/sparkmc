package sparkmc.net;

import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.util.Reporter;
import sparkmc.util.Util;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PaperProvider implements Provider {
    private static final String BASE = "https://fill.papermc.io/v3/projects/paper";

    @Override
    public List<String> versions() throws Exception {
        Project project = NetUtil.getJson(BASE, Project.class);
        Set<String> set = new LinkedHashSet<>();
        if (project.versions != null) {
            for (List<String> group : project.versions.values()) {
                for (String v : group) {
                    if (isRelease(v)) {
                        set.add(v);
                    }
                }
            }
        }
        List<String> out = new ArrayList<>(set);
        out.sort((a, b) -> Util.cmpVersion(b, a));
        return out;
    }

    @Override
    public LaunchTarget prepare(String version, LoaderChannel channel, Path dir, Reporter rep)
            throws Exception {
        Type t = NetUtil.listOf(Build.class);
        List<Build> builds = NetUtil.getJson(BASE + "/versions/" + version + "/builds", t);
        if (builds == null || builds.isEmpty()) {
            throw new IllegalStateException("no builds for " + version);
        }
        Build build = null;
        for (Build b : builds) {
            if ("STABLE".equalsIgnoreCase(b.channel)) {
                build = b;
                break;
            }
        }
        if (build == null) {
            build = builds.get(0);
        }
        Download dl = null;
        if (build.downloads != null) {
            dl = build.downloads.get("server:default");
            if (dl == null && !build.downloads.isEmpty()) {
                dl = build.downloads.values().iterator().next();
            }
        }
        if (dl == null || dl.url == null) {
            throw new IllegalStateException("no download for paper " + version + " build " + build.id);
        }
        rep.log("Using Paper " + version + " build " + build.id + " (" + build.channel + ")");
        Path path = dir.resolve("server.jar");
        NetUtil.downloadLogged(dl.url, path, rep);
        return LaunchTarget.jar(path);
    }

    private static boolean isRelease(String v) {
        String lower = v.toLowerCase(Locale.ROOT);
        return !(lower.contains("pre")
                || lower.contains("rc")
                || lower.contains("snapshot")
                || lower.contains("alpha")
                || lower.contains("beta"));
    }

    private static final class Project {
        Map<String, List<String>> versions;
    }

    private static final class Build {
        int id;
        String channel;
        Map<String, Download> downloads;
    }

    private static final class Download {
        String url;
    }
}
