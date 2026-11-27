package airport.cli;

import airport.analysis.DisruptionAnalyzer;
import airport.bench.BenchmarkRunner;
import airport.bst.RunwayBST;
import airport.bst.RunwayScheduler;
import airport.crisis.CrisisEngine;
import airport.crisis.CrisisReport;
import airport.io.CsvLoader;
import airport.io.LoadResult;
import airport.model.Flight;
import airport.model.FlightStatus;
import airport.model.TimeUtil;
import airport.registry.FlightHashTable;
import airport.sort.CountingSort;
import airport.sort.RadixSort;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * The ONLY class that changes state. Every mutation updates the registry and the
 * BST in the same method, then runs checkConsistency.
 */
public class CommandProcessor {

    /** A user error: printed as "ERROR: message", never a stack trace. */
    static class CmdException extends RuntimeException {
        CmdException(String m) { super(m); }
    }

    private FlightHashTable registry = new FlightHashTable();
    private RunwayScheduler scheduler = new RunwayScheduler();
    private CrisisEngine crisis = new CrisisEngine(registry, scheduler);

    private final PrintStream out;
    private final Scanner in;           // null in batch mode
    private boolean running = true;

    public CommandProcessor(PrintStream out, Scanner in) {
        this.out = out;
        this.in = in;
    }

    public boolean isRunning() { return running; }
    public FlightHashTable registry() { return registry; }
    public RunwayScheduler scheduler() { return scheduler; }

    // ------------------------------------------------------------ tokenizer

    /** Split on spaces; text inside double quotes is a single token. */
    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false, has = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQuote = !inQuote; has = true; }
            else if (Character.isWhitespace(c) && !inQuote) {
                if (has) { tokens.add(cur.toString()); cur.setLength(0); has = false; }
            } else { cur.append(c); has = true; }
        }
        if (inQuote) throw new CmdException("missing closing quote");
        if (has) tokens.add(cur.toString());
        return tokens;
    }

    // ------------------------------------------------------------ entry point

    public void execute(String line) {
        try {
            List<String> t = tokenize(line.trim());
            if (t.isEmpty()) return;
            dispatch(t);
        } catch (CmdException e) {
            out.println("ERROR: " + e.getMessage());
        } catch (RuntimeException e) {
            out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private void dispatch(List<String> t) {
        String cmd = t.get(0).toUpperCase();
        switch (cmd) {
            case "LOAD": argc(t, 1, "LOAD <csv>"); load(t.get(1)); break;
            case "LIST_RUNWAY": argc(t, 1, "LIST_RUNWAY <R>"); listRunway(t.get(1)); break;
            case "FIND": argc(t, 1, "FIND <id>"); find(t.get(1)); break;
            case "ADD": argc(t, 8, "ADD <id> <airline> <orig> <dest> <R> \"<start>\" \"<end>\" <prio>"); add(t); break;
            case "CANCEL": argc(t, 1, "CANCEL <id>"); cancel(t.get(1)); break;
            case "RESCHEDULE": argc(t, 2, "RESCHEDULE <id> \"<newStart>\""); reschedule(t.get(1), t.get(2)); break;
            case "CHECK_CONFLICT": argc(t, 3, "CHECK_CONFLICT <R> \"<start>\" \"<end>\""); checkConflict(t.get(1), t.get(2), t.get(3)); break;
            case "RUNWAY_CLOSURE": argc(t, 3, "RUNWAY_CLOSURE <R> \"<from>\" \"<to>\""); closure(t.get(1), t.get(2), t.get(3)); break;
            case "EMERGENCY_INSERT": emergency(t); break;
            case "MASS_CANCEL": argc(t, 2, "MASS_CANCEL \"<from>\" \"<to>\""); massCancel(t.get(1), t.get(2)); break;
            case "SORT_PRIORITY": sort(t, true); break;
            case "SORT_ID": sort(t, false); break;
            case "ANALYZE": argc(t, 0, "ANALYZE"); needData(); out.println(DisruptionAnalyzer.analyze(registry)); break;
            case "BENCHMARK": argc(t, 1, "BENCHMARK <hash|bst|sort|matrix|all>"); benchmark(t.get(1)); break;
            case "STATS": argc(t, 0, "STATS"); stats(); break;
            case "TRACE": argc(t, 1, "TRACE ON|OFF"); trace(t.get(1)); break;
            case "EXIT": running = false; break;
            default: throw new CmdException("unknown command '" + t.get(0) + "'");
        }
    }

    // ------------------------------------------------------------ helpers

    private static void argc(List<String> t, int n, String usage) {
        if (t.size() - 1 != n) throw new CmdException("wrong number of arguments. Usage: " + usage);
    }

    private static long time(String s) {
        try { return TimeUtil.parse(s); }
        catch (RuntimeException e) { throw new CmdException("bad time '" + s + "' (expected yyyy-MM-dd HH:mm)"); }
    }

    private String runway(String r) {
        if (scheduler.hasRunway(r)) return r;
        throw new CmdException("unknown runway " + r);
    }

    private Flight existing(String id) {
        Flight f = registry.get(id);
        if (f == null) throw new CmdException("unknown flight ID " + id);
        return f;
    }

    private void needData() {
        if (registry.size() == 0) throw new CmdException("no data loaded. Use LOAD <csv> first");
    }

    private static String clock(long m) { return TimeUtil.format(m).substring(11); }

    private static String describe(Flight f) {
        return f.id + " " + f.airline + " " + f.origin + "->" + f.destination + " " + f.runway + " "
                + TimeUtil.format(f.start) + "-" + clock(f.end) + " p" + f.priority + " " + f.status;
    }

    private static String ids(List<Flight> fl) {
        StringBuilder sb = new StringBuilder();
        for (Flight f : fl) { if (sb.length() > 0) sb.append(", "); sb.append(f.id); }
        return sb.toString();
    }

    /** Runs the registry<->BST consistency check after every mutation. */
    private List<String> consistency() {
        List<String> problems = scheduler.checkConsistency(registry);
        if (!problems.isEmpty()) out.println("WARNING: consistency check failed: " + problems);
        return problems;
    }

    /** Builds and validates a flight from tokens t[1..8]. */
    private Flight buildFlight(List<String> t) {
        String id = t.get(1);
        if (id.isEmpty()) throw new CmdException("empty flight ID");
        if (registry.get(id) != null) throw new CmdException("duplicate ID " + id);
        String rw = runway(t.get(5));
        long s = time(t.get(6)), e = time(t.get(7));
        if (s >= e) throw new CmdException("start must be before end");
        int prio;
        try { prio = Integer.parseInt(t.get(8)); }
        catch (NumberFormatException ex) { throw new CmdException("priority must be 1, 2 or 3"); }
        if (prio < 1 || prio > 3) throw new CmdException("priority must be 1, 2 or 3");
        // ADAPT HERE if P1's Flight constructor differs
        return new Flight(id, t.get(2), t.get(3), t.get(4), rw, s, e, prio);
    }

    /** Registry first, then BST, in one place. */
    private void insertFlight(Flight f) {
        if (!registry.insert(f)) throw new CmdException("duplicate ID " + f.id);
        scheduler.add(f);
        consistency();
    }

    // ------------------------------------------------------------ commands

    private void load(String path) {
        // a missing/unreadable file is a user error: report it and KEEP the current state
        if (!java.nio.file.Files.isReadable(java.nio.file.Paths.get(path)))
            throw new CmdException("cannot read file " + path);
        // LOAD resets ALL state
        registry = new FlightHashTable();
        scheduler = new RunwayScheduler();
        crisis = new CrisisEngine(registry, scheduler);

        // P1's loader inserts every valid row into OUR registry (duplicates rejected there),
        // and returns the accepted flights; each one then goes into its runway's BST.
        LoadResult res = CsvLoader.load(path, registry);
        for (Flight f : res.flights) scheduler.add(f);
        out.println("Loaded " + registry.size() + " flights, " + res.errors.size() + " errors");
        int shown = 0;
        for (Object err : res.errors) { if (shown++ >= 10) { out.println("  ..."); break; } out.println("  " + err); }
        consistency();
    }

    private void listRunway(String r) {
        runway(r);
        List<Flight> fl = scheduler.tree(r).inorder();
        for (Flight f : fl) out.println(describe(f));
        out.println(fl.size() + " flights on " + r);
    }

    private void find(String id) {
        out.println(describe(existing(id)));
    }

    private void add(List<String> t) {
        Flight f = buildFlight(t);
        List<Flight> c = scheduler.conflicts(f.runway, f.start, f.end);
        if (!c.isEmpty()) throw new CmdException("conflicts with: " + ids(c));
        insertFlight(f);
        out.println("Added " + describe(f));
    }

    private void cancel(String id) {
        Flight f = existing(id);
        if (f.status == FlightStatus.CANCELLED) throw new CmdException("flight " + id + " is already cancelled");
        scheduler.remove(f);
        f.status = FlightStatus.CANCELLED;
        consistency();
        out.println("Cancelled " + id);
    }

    private void reschedule(String id, String newStart) {
        Flight f = existing(id);
        if (f.status == FlightStatus.CANCELLED) throw new CmdException("flight " + id + " is cancelled");
        long ns = time(newStart), dur = f.end - f.start, ne = ns + dur;
        // P2's move(): remove -> change times -> insert; on a clash it puts the flight back unchanged.
        List<Flight> c = scheduler.move(f, ns, ne);
        if (!c.isEmpty()) throw new CmdException("new slot conflicts with: " + ids(c));
        // later than planned = DELAYED; back at (or before) its original time = SCHEDULED
        f.status = ns > f.originalStart ? FlightStatus.DELAYED : FlightStatus.SCHEDULED;
        consistency();
        out.println("Rescheduled " + describe(f));
    }

    private void checkConflict(String r, String s, String e) {
        runway(r);
        long a = time(s), b = time(e);
        if (a >= b) throw new CmdException("start must be before end");
        List<Flight> c = scheduler.conflicts(r, a, b);
        out.println(c.isEmpty() ? "No conflict" : "CONFLICT with: " + ids(c));
    }

    private void closure(String r, String from, String to) {
        needData();
        runway(r);
        long a = time(from), b = time(to);
        if (a >= b) throw new CmdException("start must be before end");
        out.println(crisis.runwayClosure(r, a, b).print());
    }

    private void massCancel(String from, String to) {
        needData();
        long a = time(from), b = time(to);
        if (a >= b) throw new CmdException("start must be before end");
        out.println(crisis.massCancel(a, b).print());
    }

    private void emergency(List<String> t) {
        boolean accept = t.remove("--accept");
        argc(t, 8, "EMERGENCY_INSERT <id> <airline> <orig> <dest> <R> \"<start>\" \"<end>\" <prio> [--accept]");
        Flight f = buildFlight(t);
        List<Flight> c = crisis.emergencyConflicts(f.runway, f.start, f.end);
        if (c.isEmpty()) {
            out.println("No conflict on " + f.runway + " " + clock(f.start) + "-" + clock(f.end));
            insertFlight(f);
            out.println("Inserted " + describe(f));
            out.println("Consistency: " + (consistency().isEmpty() ? "OK" : "FAILED"));
            return;
        }
        out.println("CONFLICT with: " + ids(c));
        long dur = f.end - f.start;
        long offer = crisis.emergencyOffer(f.runway, f.start, f.end);
        out.println("Earliest free slot: " + clock(offer) + "-" + clock(offer + dur));

        if (in != null) {                                       // menu mode: ask
            out.print("Accept slot " + clock(offer) + "? (y/n) ");
            out.flush();
            accept = in.hasNextLine() && in.nextLine().trim().equalsIgnoreCase("y");
        }
        if (!accept) { out.println("Not inserted."); return; }

        // re-check the chosen slot; never move an existing flight
        if (!scheduler.conflicts(f.runway, offer, offer + dur).isEmpty())
            throw new CmdException("offered slot is no longer free");
        Flight placed = new Flight(f.id, f.airline, f.origin, f.destination, f.runway, offer, offer + dur, f.priority);
        insertFlight(placed);
        out.println("Inserted " + describe(placed));
        out.println("Consistency: " + (consistency().isEmpty() ? "OK" : "FAILED"));
    }

    private void sort(List<String> t, boolean byPriority) {
        argc(t.size() == 1 ? List.of(t.get(0), "x") : t, 1, t.get(0) + " [limit]");
        needData();
        int limit = 10;
        if (t.size() == 2) {
            try { limit = Integer.parseInt(t.get(1)); }
            catch (NumberFormatException ex) { throw new CmdException("limit must be a positive number"); }
            if (limit < 1) throw new CmdException("limit must be a positive number");
        }
        List<Flight> all = new ArrayList<>();
        for (Flight f : registry.all()) all.add(f);
        Flight[] a = all.toArray(new Flight[0]);
        a = RadixSort.byId(a);                                  // ids in order
        if (byPriority) a = CountingSort.byPriority(a);         // stable: priority, then id
        for (int i = 0; i < Math.min(limit, a.length); i++) out.println(describe(a[i]));
    }

    private void benchmark(String which) {
        String w = which.toLowerCase();
        if (!(w.equals("hash") || w.equals("bst") || w.equals("sort") || w.equals("matrix") || w.equals("all")))
            throw new CmdException("benchmark must be one of hash|bst|sort|matrix|all");
        try {
            BenchmarkRunner.run(w, false, java.nio.file.Paths.get("results"));   // CSVs go to results/
            out.println("Benchmark results written to results/bench_*.csv (plots: java -cp target/classes airport.bench.PlotBenchmarks)");
        } catch (java.io.IOException e) {
            throw new CmdException("cannot write benchmark results: " + e.getMessage());
        }
    }

    private void stats() {
        int total = 0, sched = 0, delayed = 0, cancelled = 0;
        for (Flight f : registry.all()) {
            total++;
            if (f.status == FlightStatus.SCHEDULED) sched++;
            else if (f.status == FlightStatus.DELAYED) delayed++;
            else if (f.status == FlightStatus.CANCELLED) cancelled++;
        }
        out.println("Flights: total=" + total + " scheduled=" + sched + " delayed=" + delayed + " cancelled=" + cancelled);
        // runways in alphabetical order (our radix sort), not in the order they appear in the file
        for (String r : RadixSort.sortStrings(scheduler.runways().toArray(new String[0]))) {
            RunwayBST tr = scheduler.tree(r);
            out.println("  " + r + ": flights=" + tr.size() + " BST height=" + tr.height());
        }
        out.println("Hash: " + registry.stats());
    }

    private void trace(String v) {
        if (v.equalsIgnoreCase("ON")) Trace.on = true;
        else if (v.equalsIgnoreCase("OFF")) Trace.on = false;
        else throw new CmdException("usage: TRACE ON|OFF");
        out.println("Trace " + (Trace.on ? "ON" : "OFF"));
    }
}
