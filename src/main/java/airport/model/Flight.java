package airport.model;

public class Flight {
    public final String id, airline, origin, destination;
    public String runway;
    public long start, end;                       // minutes since epoch, interval [start, end)
    public final long originalStart, originalEnd; // never change after load
    public final int priority;                    // 1 = highest
    public FlightStatus status = FlightStatus.SCHEDULED;

    public Flight(String id, String airline, String origin, String destination,
                  String runway, long start, long end, int priority) {
        this.id = id;
        this.airline = airline;
        this.origin = origin;
        this.destination = destination;
        this.runway = runway;
        this.start = start;
        this.end = end;
        this.originalStart = start;
        this.originalEnd = end;
        this.priority = priority;
    }

    public long duration() { return end - start; }
}
