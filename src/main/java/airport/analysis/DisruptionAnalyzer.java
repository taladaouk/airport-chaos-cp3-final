package airport.analysis;

import airport.model.Flight;
import airport.model.FlightStatus;
import airport.model.TimeUtil;
import airport.registry.FlightHashTable;

/** ANALYZE: finds the worst window of 5-minute buckets of delayed/cancelled flights. */
public class DisruptionAnalyzer {
    public static final int BUCKETS = 288, STEP = 5;

    public static String analyze(FlightHashTable reg) {
        long min = Long.MAX_VALUE;
        for (Flight f : reg.all()) min = Math.min(min, f.originalStart);
        if (min == Long.MAX_VALUE) return "No flights loaded.";
        long midnight = Math.floorDiv(min, 1440L) * 1440L;   // day of the earliest originalStart

        long[] load = new long[BUCKETS];
        for (Flight f : reg.all()) {
            if (f.status != FlightStatus.DELAYED && f.status != FlightStatus.CANCELLED) continue;
            long off = f.originalStart - midnight;
            if (off < 0 || off >= 1440) continue;               // not on that day
            load[(int) (off / STEP)]++;
        }

        MaxSubarray.Result dc = MaxSubarray.divideAndConquer(load);
        MaxSubarray.Result br = MaxSubarray.brute(load);

        StringBuilder sb = new StringBuilder();
        if (dc.sum == 0) sb.append("No disruption\n");
        long from = midnight + (long) dc.lo * STEP;
        long to = midnight + (long) (dc.hi + 1) * STEP;
        sb.append("Worst window: buckets [").append(dc.lo).append(", ").append(dc.hi).append("]\n");
        sb.append("Time: ").append(TimeUtil.format(from)).append(" - ").append(TimeUtil.format(to)).append('\n');
        sb.append("Disrupted flights in window: ").append(dc.sum).append('\n');
        sb.append("DC == brute: ").append(dc.sameAs(br));
        return sb.toString();
    }
}
