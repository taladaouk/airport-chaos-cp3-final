package airport.crisis;

import airport.model.Flight;
import airport.model.FlightStatus;
import airport.model.TimeUtil;
import java.util.ArrayList;
import java.util.List;

public class CrisisReport {
    private static class Row {
        String id, runway, oldSlot, newSlot, status, delay;
    }

    private final List<Row> rows = new ArrayList<>();
    public int affected, delayed, cancelled;
    public long totalDelay;
    public List<String> conflicts = new ArrayList<>();
    public List<String> consistency = new ArrayList<>();

    static String clock(long m) { return TimeUtil.format(m).substring(11); }

    private Row open(Flight f, long oldStart, long oldEnd) {
        Row r = new Row();
        r.id = f.id; r.runway = f.runway;
        r.oldSlot = clock(oldStart) + "-" + clock(oldEnd);
        rows.add(r);
        affected++;
        return r;
    }

    void delayed(Flight f, long oldStart, long oldEnd, long delay) {
        Row r = open(f, oldStart, oldEnd);
        r.newSlot = clock(f.start) + "-" + clock(f.end);
        r.status = FlightStatus.DELAYED.name();
        r.delay = String.valueOf(delay);
        delayed++; totalDelay += delay;
    }

    void cancelled(Flight f, long oldStart, long oldEnd) {
        Row r = open(f, oldStart, oldEnd);
        r.newSlot = "-"; r.status = FlightStatus.CANCELLED.name(); r.delay = "-";
        cancelled++;
    }

    public String print() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s %-4s %-13s %-13s %-10s %s%n", "ID", "RWY", "OLD", "NEW", "STATUS", "DELAY"));
        for (Row r : rows)
            sb.append(String.format("%-10s %-4s %-13s %-13s %-10s %s%n", r.id, r.runway, r.oldSlot, r.newSlot, r.status, r.delay));
        sb.append("Affected: ").append(affected).append("  Delayed: ").append(delayed)
          .append("  Cancelled: ").append(cancelled).append("  Total delay: ").append(totalDelay)
          .append(" min  Conflicts: ").append(conflicts.isEmpty() ? "none" : String.join("; ", conflicts)).append('\n');
        sb.append("Consistency: ").append(consistency.isEmpty() ? "OK" : "FAILED " + consistency);
        return sb.toString();
    }
}
