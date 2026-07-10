package sparkmc.net;

import sparkmc.model.LaunchTarget;
import sparkmc.model.LoaderChannel;
import sparkmc.util.Reporter;

import java.nio.file.Path;
import java.util.List;

public interface Provider {
    List<String> versions() throws Exception;

    LaunchTarget prepare(String version, LoaderChannel channel, Path dir, Reporter rep) throws Exception;
}
