package sparkmc;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        ProcessGuard.install();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("[sparkmc] fatal: " + e.getMessage());
            ProcessGuard.killCurrent();
        });

        Path dir = Path.of("").toAbsolutePath();
        if (args.length == 0) {
            if (LaunchPlan.exists(dir)) {
                ConsoleApp.run(dir);
            } else {
                Wizard.run(dir);
            }
            return;
        }
        switch (args[0]) {
            case "--run" -> ConsoleApp.run(dir);
            case "--help", "-h" -> printHelp();
            default -> {
                System.err.println("unknown argument: " + args[0]);
                printHelp();
                System.exit(2);
            }
        }
    }

    private static void printHelp() {
        System.out.println(
                """
                sparkmc - Minecraft server setup & console (Java)

                Usage:
                  java -jar sparkmc.jar           interactive setup or run existing server
                  java -jar sparkmc.jar --run     run server from sparkmc.json
                  java -jar sparkmc.jar --help    show this help
                """);
    }
}
