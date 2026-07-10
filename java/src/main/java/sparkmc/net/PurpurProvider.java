package sparkmc.net;

import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.util.Reporter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PurpurProvider implements Provider {
    private static final String BASE = "https://api.purpurmc.org/v2/purpur";

    @Override
    public List<String> versions() throws Exception {
        Project project = Http.getJson(BASE, Project.class);
        List<String> versions = new ArrayList<>(project.versions);
        Collections.reverse(versions);
        return versions;
    }

    @Override
    public LaunchTarget prepare(String version, LoaderChannel channel, Path dir, Reporter rep)
            throws Exception {
        VersionInfo info = Http.getJson(BASE + "/" + version, VersionInfo.class);
        String url = BASE + "/" + version + "/" + info.builds.latest + "/download";
        Path path = dir.resolve("server.jar");
        NetUtil.downloadLogged(url, path, rep);
        return LaunchTarget.jar(path);
    }

    private static final class Project {
        List<String> versions;
    }

    private static final class VersionInfo {
        BuildRef builds;
    }

    private static final class BuildRef {
        String latest;
    }
}
