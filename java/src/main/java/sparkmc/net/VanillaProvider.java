package sparkmc.net;

import com.google.gson.annotations.SerializedName;
import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.util.Reporter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class VanillaProvider implements Provider {
    private static final String MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    @Override
    public List<String> versions() throws Exception {
        Manifest m = Http.getJson(MANIFEST, Manifest.class);
        List<String> out = new ArrayList<>();
        for (Entry e : m.versions) {
            if ("release".equals(e.type)) {
                out.add(e.id);
            }
        }
        return out;
    }

    @Override
    public LaunchTarget prepare(String version, LoaderChannel channel, Path dir, Reporter rep)
            throws Exception {
        Manifest m = Http.getJson(MANIFEST, Manifest.class);
        Entry entry = null;
        for (Entry e : m.versions) {
            if (version.equals(e.id)) {
                entry = e;
                break;
            }
        }
        if (entry == null) {
            throw new IllegalStateException("version " + version + " not found");
        }
        VersionMeta meta = Http.getJson(entry.url, VersionMeta.class);
        if (meta.downloads == null || meta.downloads.server == null) {
            throw new IllegalStateException("no server jar available for " + version);
        }
        Path path = dir.resolve("server.jar");
        NetUtil.downloadLogged(meta.downloads.server.url, path, rep);
        return LaunchTarget.jar(path);
    }

    private static final class Manifest {
        List<Entry> versions;
    }

    private static final class Entry {
        String id;

        @SerializedName("type")
        String type;

        String url;
    }

    private static final class VersionMeta {
        Downloads downloads;
    }

    private static final class Downloads {
        Artifact server;
    }

    private static final class Artifact {
        String url;
    }
}
