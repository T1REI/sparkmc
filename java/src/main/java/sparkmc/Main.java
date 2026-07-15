package sparkmc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        ProcessGuard.install();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("[sparkmc] fatal: " + e.getMessage());
            ProcessGuard.killCurrent();
        });

        boolean forceRun = false;
        List<String> passthrough = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "--run" -> forceRun = true;
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                default -> passthrough.add(arg);
            }
        }

        Path dir = Path.of("").toAbsolutePath();
        Updater.checkAndOffer();
        if (forceRun || LaunchPlan.exists(dir)) {
            ConsoleApp.run(dir, passthrough);
        } else {
            Wizard.run(dir, passthrough);
        }
    }

    private static void printHelp() {
        System.out.println(
                """
                sparkmc - Minecraft server setup & console (Java)

                Usage:
                  java [jvm flags] -jar sparkmc.jar [args]   interactive setup or run existing server
                  java -jar sparkmc.jar --run                run server from sparkmc.json
                  java -jar sparkmc.jar --help               show this help

                JVM flags used to launch sparkmc.jar are forwarded to the server JVM.
                Any other [args] are forwarded too: -X*/-D*/--add-* style go before -jar,
                the rest are appended as server arguments.
                """);
    }
}
