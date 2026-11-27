package airport.crisis;

import airport.bst.BSTNode;
import airport.bst.RunwayScheduler;
import airport.cli.Trace;
import airport.model.Flight;
import airport.model.FlightStatus;
import airport.registry.FlightHashTable;
import airport.sort.CountingSort;
import airport.sort.RadixSort;
import java.util.ArrayList;
import java.util.List;

/** Scenarios A (closure), B (emergency insert), C (mass cancel). */
public class CrisisEngine {
    public static final int MAX_DELAY = 120;   // minutes

    private final FlightHashTable registry;
    private final RunwayScheduler scheduler;

    public CrisisEngine(FlightHashTable registry, RunwayScheduler scheduler) {
        this.registry = registry;
        this.scheduler = scheduler;
    }

    /** Shared recovery routine (pseudocode is in the report). */
    public CrisisReport recover(List<Flight> affected, long disruptionEnd) {
        CrisisReport report = new CrisisReport();

        // remember pre-crisis slots for the OLD column
        int n = affected.size();
        long[] oldS = new long[n], oldE = new long[n];
        for (int i = 0; i < n; i++) { oldS[i] = affected.get(i).start; oldE[i] = affected.get(i).end; }

        // 1. take ALL affected flights out of their trees first
        for (Flight f : affected) scheduler.remove(f);

        // 2. order by (priority, originalStart, id): stable sorts, least important key first
        Flight[] a = affected.toArray(new Flight[0]);
        a = RadixSort.byId(a);
        a = RadixSort.stableSortByStart(a);
        a = CountingSort.byPriority(a);

        // 3. place each flight on its ORIGINAL runway
        for (Flight f : a) {
            int idx = indexOf(affected, f);
            long dur = f.originalEnd - f.originalStart;
            long latest = f.originalStart + MAX_DELAY;
            long t = scheduler.earliestSlot(f.runway, Math.max(disruptionEnd, f.originalStart), dur, latest);
            if (t != -1) {
                f.start = t; f.end = t + dur; f.status = FlightStatus.DELAYED;
                scheduler.add(f);
                report.delayed(f, oldS[idx], oldE[idx], t - f.originalStart);
                Trace.log("recover " + f.id + " (p" + f.priority + ") -> delayed to " + airport.model.TimeUtil.format(t) + " (+" + (t - f.originalStart) + " min)");
            } else {
                f.status = FlightStatus.CANCELLED;      // stays in registry, not in any tree
                report.cancelled(f, oldS[idx], oldE[idx]);
                Trace.log("recover " + f.id + " (p" + f.priority + ") -> CANCELLED");
            }
        }
        report.consistency = scheduler.checkConsistency(registry);
        return report;
    }

    private static int indexOf(List<Flight> l, Flight f) {
        for (int i = 0; i < l.size(); i++) if (l.get(i) == f) return i;
        return -1;
    }

    /** Scenario A. */
    public CrisisReport runwayClosure(String runway, long from, long to) {
        List<Flight> affected = new ArrayList<>();
        BSTNode pred = scheduler.tree(runway).predecessor(from, "");
        if (pred != null && pred.flight().end > from) affected.add(pred.flight());
        for (Flight f : scheduler.tree(runway).rangeSearch(from, to))
            if (f.start < to && f.end > from) affected.add(f);
        return recover(affected, to);
    }

    /** Scenario C. */
    public CrisisReport massCancel(long from, long to) {
        CrisisReport report = new CrisisReport();
        List<Flight> hit = new ArrayList<>();
        for (String r : scheduler.runways())
            for (Flight f : scheduler.tree(r).rangeSearch(from, to))
                if (f.status == FlightStatus.SCHEDULED || f.status == FlightStatus.DELAYED) hit.add(f);
        for (Flight f : hit) {
            long s = f.start, e = f.end;
            scheduler.remove(f);
            f.status = FlightStatus.CANCELLED;
            report.cancelled(f, s, e);
        }
        report.consistency = scheduler.checkConsistency(registry);
        return report;
    }

    /** Scenario B step 1: flights blocking the requested slot (empty list = free). */
    public List<Flight> emergencyConflicts(String runway, long s, long e) {
        return scheduler.conflicts(runway, s, e);
    }

    /**
     * Scenario B step 2: the earliest feasible LATER slot, trying s+5, s+10, ... (5-minute increments,
     * same duration). For the official 5-minute request this is the same as starting at the requested end.
     */
    public long emergencyOffer(String runway, long s, long e) {
        return scheduler.earliestSlot(runway, s + 5, e - s, Long.MAX_VALUE / 4);
    }
}
