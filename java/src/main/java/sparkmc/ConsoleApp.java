package sparkmc;

import sparkmc.util.Ansi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ConsoleApp {
    private static final Object OUT_LOCK = new Object();
    private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);
    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);

    /** Sentinel: stdin closed / EOF */
    private static final String EOF = "\0EOF";

    private ConsoleApp() {}

    public static void run(Path dir) {
        run(dir, List.of());
    }

    public static void run(Path dir, List<String> extraArgs) {
        ProcessGuard.install();
        installLocalHook();

        LaunchPlan plan;
        try {
            plan = LaunchPlan.load(dir);
        } catch (Exception e) {
            println(Ansi.RED + "sparkmc: " + e.getMessage() + Ansi.RESET);
            waitEnter();
            return;
        }

        String program = plan.program();
        Set<Integer> tried = new HashSet<>();

        if (plan.required_java != null) {
            String resolved = ensureJava(plan.required_java, program, plan, dir, tried);
            if (resolved == null) {
                return;
            }
            program = resolved;
        }

        // Single blocking stdin reader for the whole console session.
        BlockingQueue<String> inputQueue = new ArrayBlockingQueue<>(256);
        AtomicBoolean stdinDead = new AtomicBoolean(false);
        Thread inputThread = new Thread(() -> stdinLoop(inputQueue, stdinDead), "sparkmc-input");
        inputThread.setDaemon(true);
        inputThread.start();

        while (!SHUTTING_DOWN.get() && !stdinDead.get()) {
            List<String> args = plan.args(extraArgs);
            system(program + " " + String.join(" ", args));

            Process process;
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(program);
                cmd.addAll(args);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(dir.toFile());
                pb.redirectErrorStream(true);
                process = pb.start();
                ProcessGuard.track(process);
            } catch (Exception e) {
                system("failed to start java: " + e.getMessage());
                waitEnter(inputQueue);
                return;
            }

            AtomicReference<Integer> needed = new AtomicReference<>();
            Thread pump = new Thread(() -> pump(process.getInputStream(), needed), "sparkmc-pump");
            pump.setDaemon(true);
            pump.start();

            system("server started");
            printPrompt();

            boolean consoleClosed = false;
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                while (process.isAlive() && !SHUTTING_DOWN.get() && !stdinDead.get()) {
                    String line = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (line == null) {
                        continue;
                    }
                    if (EOF.equals(line)) {
                        consoleClosed = true;
                        break;
                    }
                    if (line.isEmpty()) {
                        printPrompt();
                        continue;
                    }
                    // Send exactly once. Use '\n' only (Minecraft accepts it on Windows too).
                    writer.write(line);
                    writer.write('\n');
                    writer.flush();
                    // Do NOT re-print the line: the terminal already echoed keystrokes.
                    printPrompt();
                }
            } catch (IOException e) {
                consoleClosed = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                consoleClosed = true;
            }

            if (consoleClosed || stdinDead.get() || SHUTTING_DOWN.get()) {
                ProcessGuard.kill(process);
                ProcessGuard.clear(process);
                system("console closed - server stopped");
                return;
            }

            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ProcessGuard.kill(process);
                ProcessGuard.clear(process);
                return;
            } finally {
                ProcessGuard.clear(process);
            }

            try {
                pump.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            int code;
            try {
                code = process.exitValue();
            } catch (IllegalThreadStateException e) {
                ProcessGuard.kill(process);
                code = -1;
            }
            system("process exited (" + code + ")");

            Integer major = needed.get();
            if (major != null) {
                String resolved = ensureJava(major, program, plan, dir, tried, inputQueue);
                if (resolved == null) {
                    return;
                }
                program = resolved;
                continue;
            }

            system("server stopped, press Enter to close");
            waitEnter(inputQueue);
            return;
        }
    }

    private static void stdinLoop(BlockingQueue<String> queue, AtomicBoolean stdinDead) {
        // One reader. Blocking readLine keeps console echo so typing is visible.
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (!SHUTTING_DOWN.get()) {
                String line = in.readLine();
                if (line == null) {
                    stdinDead.set(true);
                    queue.offer(EOF);
                    return;
                }
                // Put even empty lines; consumer decides. Empty = just redraw prompt.
                if (!queue.offer(line)) {
                    try {
                        queue.put(line);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        stdinDead.set(true);
                        return;
                    }
                }
            }
        } catch (IOException e) {
            stdinDead.set(true);
            queue.offer(EOF);
        }
    }

    private static void installLocalHook() {
        if (!HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> SHUTTING_DOWN.set(true), "sparkmc-flag"));
    }

    private static void pump(InputStream in, AtomicReference<Integer> neededMajor) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                JavaRuntime.neededMajorFromLine(line).ifPresent(neededMajor::set);
                // Newline first so we don't erase half-typed input with \r wipe.
                // User may see a broken partial line above; that's better than invisible typing.
                synchronized (OUT_LOCK) {
                    System.out.println();
                    System.out.println(colorFor(line) + line + Ansi.RESET);
                    System.out.print(Ansi.GREEN + ">" + Ansi.RESET + " ");
                    System.out.flush();
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String ensureJava(int major, String current, LaunchPlan plan, Path dir, Set<Integer> tried) {
        return ensureJava(major, current, plan, dir, tried, null);
    }

    private static String ensureJava(
            int major,
            String current,
            LaunchPlan plan,
            Path dir,
            Set<Integer> tried,
            BlockingQueue<String> inputQueue) {
        if (JavaRuntime.installedMajor(current).orElse(-1) == major) {
            return current;
        }
        Optional<Path> found = JavaRuntime.resolve(major, plan.java);
        if (found.isPresent()) {
            String program = found.get().toString();
            if (!program.equals(current)) {
                system("found Java " + major + ": " + program);
            }
            plan.java = program;
            try {
                plan.save(dir);
            } catch (IOException ignored) {
            }
            return program;
        }
        if (tried.contains(major)) {
            system("still failing with Java " + major + ", aborting");
            waitEnter(inputQueue);
            return null;
        }
        tried.add(major);
        system("Java " + major + " not found on this system");
        println(Ansi.YELLOW + "[sparkmc] This server needs Java " + major
                + ". Install it (e.g. https://adoptium.net) and run sparkmc again." + Ansi.RESET);
        system("Attempting to launch the server with the current Java version anyway...");
        return current;
    }

    private static void system(String msg) {
        println(Ansi.GREEN + "[sparkmc]" + Ansi.RESET + " " + Ansi.WHITE + msg + Ansi.RESET);
    }

    private static void waitEnter() {
        waitEnter(null);
    }

    private static void waitEnter(BlockingQueue<String> inputQueue) {
        printPrompt();
        readFrom(inputQueue);
    }

    private static String readFrom(BlockingQueue<String> inputQueue) {
        if (inputQueue != null) {
            try {
                String line = inputQueue.take();
                return EOF.equals(line) ? null : line;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        try {
            return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private static void println(String msg) {
        synchronized (OUT_LOCK) {
            System.out.println(msg);
            System.out.flush();
        }
    }

    private static void printPrompt() {
        synchronized (OUT_LOCK) {
            System.out.print(Ansi.GREEN + ">" + Ansi.RESET + " ");
            System.out.flush();
        }
    }

    private static String colorFor(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (containsAny(upper, "FATAL", "SEVERE", "/ERROR", "ERROR]", " ERROR", "EXCEPTION", "CAUSED BY", "\tAT ")) {
            return Ansi.RED;
        }
        if (containsAny(upper, "/WARN", "WARN]", " WARN", "WARNING")) {
            return Ansi.YELLOW;
        }
        if (containsAny(upper, "/DEBUG", "DEBUG]", "/TRACE", "TRACE]")) {
            return Ansi.GRAY;
        }
        if (containsAny(upper, "DONE (", "]: DONE", "FOR HELP, TYPE")) {
            return Ansi.GREEN;
        }
        if (containsAny(upper, "STARTING", "PREPARING", "LOADING", "RELOADING")) {
            return Ansi.CYAN;
        }
        return Ansi.WHITE;
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
