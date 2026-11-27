package airport.cli;

/** Global TRACE switch. Everyone calls Trace.log(...). */
public class Trace {
    public static boolean on = false;

    public static void log(String msg) {
        if (on) System.out.println("[TRACE] " + msg);
    }
}
