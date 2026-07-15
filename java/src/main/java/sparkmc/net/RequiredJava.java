package sparkmc.net;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class RequiredJava {
    private static final String MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private RequiredJava() {}

    public static Integer forMcVersion(String mcVersion) {
        try {
            Manifest m = NetUtil.getJson(MANIFEST, Manifest.class);
            for (Entry e : m.versions) {
                if (mcVersion.equals(e.id)) {
                    VersionMeta meta = NetUtil.getJson(e.url, VersionMeta.class);
                    if (meta.javaVersion != null) {
                        return meta.javaVersion.majorVersion;
                    }
                    return null;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final class Manifest {
        List<Entry> versions;
    }

    private static final class Entry {
        String id;
        String url;
    }

    private static final class VersionMeta {
        @SerializedName("javaVersion")
        JavaComponent javaVersion;
    }

    private static final class JavaComponent {
        @SerializedName("majorVersion")
        int majorVersion;
    }
}
