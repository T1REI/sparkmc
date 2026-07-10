package sparkmc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ties the Minecraft server child process to the sparkmc JVM lifetime on
 * Windows, Linux and macOS.
 *
 * <ul>
 *   <li>shutdown hook</li>
 *   <li>{@code Process.destroyOnExit()} via reflection when available (JDK 20+)</li>
 *   <li>external watchdog that kills the server tree if sparkmc dies
 *       (console closed without hooks)</li>
 * </ul>
 */
public final class ProcessGuard {
    private static final AtomicReference<Process> CURRENT = new AtomicReference<>();
    private static final AtomicReference<Process> WATCHDOG = new AtomicReference<>();
    private static final Method DESTROY_ON_EXIT = findDestroyOnExit();
    private static volatile boolean hookInstalled;

    private ProcessGuard() {}

    public static synchronized void install() {
        if (hookInstalled) {
            return;
        }
        hookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopWatchdog();
            Process p = CURRENT.getAndSet(null);
            if (p != null) {
                kill(p);
            }
            try {
                ProcessHandle.current().children().forEach(ph -> {
                    try {
                        ph.destroyForcibly();
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }, "sparkmc-process-guard"));
    }

    public static void track(Process process) {
        install();
        Process previous = CURRENT.getAndSet(process);
        if (previous != null && previous != process && previous.isAlive()) {
            kill(previous);
        }
        attachDestroyOnExit(process);
        startWatchdog(process);
    }

    public static void clear(Process process) {
        CURRENT.compareAndSet(process, null);
        stopWatchdog();
    }

    public static void killCurrent() {
        stopWatchdog();
        Process p = CURRENT.getAndSet(null);
        if (p != null) {
            kill(p);
        }
    }

    private static Method findDestroyOnExit() {
        try {
            return Process.class.getMethod("destroyOnExit");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static void attachDestroyOnExit(Process process) {
        if (DESTROY_ON_EXIT == null || process == null) {
            return;
        }
        try {
            DESTROY_ON_EXIT.invoke(process);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /**
     * External watcher: when sparkmc PID disappears, kill the server process tree.
     * Works when the console is closed and JVM hooks never run.
     */
    private static void startWatchdog(Process child) {
        stopWatchdog();
        if (child == null) {
            return;
        }

        long parentPid;
        long childPid;
        try {
            parentPid = ProcessHandle.current().pid();
            childPid = child.pid();
        } catch (Exception e) {
            return;
        }

        try {
            Process w;
            if (isWindows()) {
                w = startWindowsWatchdog(parentPid, childPid);
            } else {
                w = startUnixWatchdog(parentPid, childPid);
            }
            if (w != null) {
                WATCHDOG.set(w);
            }
        } catch (Exception ignored) {
        }
    }

    private static Process startWindowsWatchdog(long parentPid, long childPid) throws Exception {
        String script = "$p=" + parentPid + "; $c=" + childPid + "; "
                + "while (Get-Process -Id $p -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 400 } "
                + "taskkill /F /T /PID $c 2>$null | Out-Null";
        try {
            return new ProcessBuilder(
                            "powershell",
                            "-NoProfile",
                            "-NonInteractive",
                            "-WindowStyle",
                            "Hidden",
                            "-Command",
                            script)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception primary) {
            String cmd = "cmd /c \"@echo off & :loop & tasklist /FI \"PID eq "
                    + parentPid
                    + "\" | find \""
                    + parentPid
                    + "\" >nul & if not errorlevel 1 (timeout /t 1 /nobreak >nul & goto loop) "
                    + "& taskkill /F /T /PID "
                    + childPid
                    + " >nul 2>&1\"";
            return new ProcessBuilder("cmd", "/c", cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
    }

    private static Process startUnixWatchdog(long parentPid, long childPid) throws Exception {
        // Portable sh: wait until parent dies, then TERM/KILL the whole child tree.
        // Works on Linux (glibc/musl), most BSDs and macOS (bash/sh + kill/pgrep|ps).
        String script = ""
                + "P=\"" + parentPid + "\"; C=\"" + childPid + "\"; "
                + "while kill -0 \"$P\" 2>/dev/null; do sleep 0.4; done; "
                + "killtree() { "
                + "  pid=\"$1\"; sig=\"$2\"; "
                + "  for c in $(pgrep -P \"$pid\" 2>/dev/null || true); do killtree \"$c\" \"$sig\"; done; "
                + "  if [ -z \"$(pgrep -P \"$pid\" 2>/dev/null || true)\" ]; then "
                + "    for c in $(ps -o pid= --ppid \"$pid\" 2>/dev/null || ps -o pid= -g \"$pid\" 2>/dev/null || true); do "
                + "      case \"$c\" in ''|*[!0-9]* ) ;; *) killtree \"$c\" \"$sig\" ;; esac; "
                + "    done; "
                + "  fi; "
                + "  kill -s \"$sig\" \"$pid\" 2>/dev/null || true; "
                + "}; "
                + "killtree \"$C\" TERM; "
                + "sleep 1; "
                + "killtree \"$C\" KILL; "
                + "kill -KILL \"$C\" 2>/dev/null || true";

        List<String> cmd = new ArrayList<>();
        // Prefer bare sh for all distros; fall back to bash/dash if needed.
        if (isExecutable("/bin/sh")) {
            cmd.add("/bin/sh");
        } else if (isExecutable("/usr/bin/sh")) {
            cmd.add("/usr/bin/sh");
        } else {
            cmd.add("sh");
        }
        cmd.add("-c");
        cmd.add(script);

        return new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static boolean isExecutable(String path) {
        try {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            return java.nio.file.Files.isExecutable(p);
        } catch (Exception e) {
            return false;
        }
    }

    private static void stopWatchdog() {
        Process w = WATCHDOG.getAndSet(null);
        if (w == null) {
            return;
        }
        try {
            w.destroyForcibly();
        } catch (Exception ignored) {
        }
        if (isWindows()) {
            try {
                long pid = w.pid();
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", Long.toString(pid))
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        } else {
            try {
                long pid = w.pid();
                new ProcessBuilder("kill", "-KILL", Long.toString(pid))
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    public static void kill(Process process) {
        if (process == null) {
            return;
        }

        long pid = -1L;
        try {
            pid = process.pid();
        } catch (Exception ignored) {
        }

        try {
            process.descendants().forEach(ph -> {
                try {
                    ph.destroy();
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        try {
            process.destroy();
        } catch (Exception ignored) {
        }

        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.descendants().forEach(ph -> {
                    try {
                        ph.destroyForcibly();
                    } catch (Exception ignored) {
                    }
                });
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
        }

        if (pid > 0) {
            if (isWindows()) {
                taskkill(pid);
            } else {
                unixKillTree(pid);
            }
        }

        if (process.isAlive()) {
            try {
                process.toHandle().descendants().forEach(ph -> {
                    try {
                        ph.destroyForcibly();
                    } catch (Exception ignored) {
                    }
                });
                process.toHandle().destroyForcibly();
            } catch (Exception ignored) {
            }
            if (pid > 0) {
                if (isWindows()) {
                    taskkill(pid);
                } else {
                    unixKillTree(pid);
                }
            }
        }
    }

    private static void taskkill(long pid) {
        try {
            Process killer = new ProcessBuilder("taskkill", "/F", "/T", "/PID", Long.toString(pid))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!killer.waitFor(5, TimeUnit.SECONDS)) {
                killer.destroyForcibly();
            }
        } catch (Exception ignored) {
        }
    }

    private static void unixKillTree(long pid) {
        // Soft then hard, including children via pkill -P (Linux/macOS) and kill.
        try {
            new ProcessBuilder("sh", "-c",
                            "killtree(){ for c in $(pgrep -P \"$1\" 2>/dev/null); do killtree \"$c\"; done; "
                                    + "kill -s \"$2\" \"$1\" 2>/dev/null || true; }; "
                                    + "killtree "
                                    + pid
                                    + " TERM; sleep 0.5; killtree "
                                    + pid
                                    + " KILL")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            try {
                new ProcessBuilder("kill", "-TERM", Long.toString(pid))
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor(2, TimeUnit.SECONDS);
                new ProcessBuilder("kill", "-KILL", Long.toString(pid))
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor(2, TimeUnit.SECONDS);
            } catch (Exception ignored2) {
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
