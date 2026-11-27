package airport.cli;

public final class Trace {
    public static boolean on = false;
    private Trace() {}
    public static void log(String msg) { if (on) System.out.println("[TRACE] " + msg); }
}