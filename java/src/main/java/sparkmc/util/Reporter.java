package sparkmc.util;

public final class Reporter {
    public void log(String msg) {
        System.out.println(Ansi.GREEN + "[sparkmc]" + Ansi.RESET + " " + Ansi.WHITE + msg + Ansi.RESET);
    }
}
