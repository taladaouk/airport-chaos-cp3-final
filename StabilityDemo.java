package airport.sort;

import airport.model.Flight;

/**
 * Stability demo for the report (Part D).
 * 6 flights with mixed priorities: sort by ID first, then counting-sort by priority.
 * Flights with the same priority must still be in ID order.
 *
 * Run:  java -cp target/classes airport.sort.StabilityDemo
 */
public final class StabilityDemo {
    public static void main(String[] args) {
        long t0 = 29_400_000L; // any start time; not used by these sorts
        Flight[] flights = {
            new Flight("UA3001", "UA", "JFK", "BOS", "R1", t0,      t0 + 5,  2),
            new Flight("AA1002", "AA", "JFK", "ORD", "R2", t0 + 5,  t0 + 10, 1),
            new Flight("DL5003", "DL", "LAX", "JFK", "R1", t0 + 10, t0 + 15, 3),
            new Flight("B6404",  "B6", "BOS", "MIA", "R3", t0 + 15, t0 + 20, 2),
            new Flight("AA0999", "AA", "ORD", "LAX", "R2", t0 + 20, t0 + 25, 3),
            new Flight("DL2005", "DL", "MIA", "JFK", "R4", t0 + 25, t0 + 30, 1),
        };

        print("Input order", flights);
        Flight[] byId = RadixSort.byId(flights);
        print("Step 1: radix sort by flight_id", byId);
        Flight[] byPrio = CountingSort.byPriority(byId);
        print("Step 2: counting sort by priority (stable)", byPrio);

        System.out.println("Check: within each priority group the IDs are still in alphabetical order:");
        boolean ok = true;
        for (int i = 1; i < byPrio.length; i++)
            if (byPrio[i].priority == byPrio[i - 1].priority && byPrio[i].id.compareTo(byPrio[i - 1].id) < 0) ok = false;
        System.out.println(ok ? "  YES -> counting sort is stable" : "  NO -> NOT stable");
    }

    private static void print(String title, Flight[] a) {
        System.out.println(title);
        System.out.println("  pos | flight_id | priority");
        for (int i = 0; i < a.length; i++)
            System.out.printf("  %3d | %-9s | %d%n", i, a[i].id, a[i].priority);
        System.out.println();
    }
}
