package sparkmc.net;

import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.util.Reporter;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class FabricProvider implements Provider {
    private static final String META = "https://meta.fabricmc.net/v2/versions";

    @Override
    public List<String> versions() throws Exception {
        Type t = NetUtil.listOf(Game.class);
        List<Game> games = NetUtil.getJson(META + "/game", t);
        List<String> out = new ArrayList<>();
        for (Game g : games) {
            if (g.stable) {
                out.add(g.version);
            }
        }
        return out;
    }

    @Override
    public LaunchTarget prepare(String version, LoaderChannel channel, Path dir, Reporter rep)
            throws Exception {
        Type t = NetUtil.listOf(Named.class);
        List<Named> loaders = NetUtil.getJson(META + "/loader", t);
        List<Named> installers = NetUtil.getJson(META + "/installer", t);
        if (loaders.isEmpty() || installers.isEmpty()) {
            throw new IllegalStateException("fabric loader/installer unavailable");
        }
        String loader = loaders.get(0).version;
        String installer = installers.get(0).version;
        String url = META + "/loader/" + version + "/" + loader + "/" + installer + "/server/jar";
        Path path = dir.resolve("server.jar");
        NetUtil.downloadLogged(url, path, rep);
        return LaunchTarget.jar(path);
    }

    private static final class Game {
        String version;
        boolean stable;
    }

    private static final class Named {
        String version;
    }
}
