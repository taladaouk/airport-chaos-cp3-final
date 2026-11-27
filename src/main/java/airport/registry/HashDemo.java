package airport.registry;

import airport.cli.Trace;
import airport.io.CsvLoader;
import airport.io.LoadResult;

public class HashDemo {
    public static void main(String[] args) {
        FlightHashTable t = new FlightHashTable();
        LoadResult r = CsvLoader.load("data/airport_flights.csv", t);
        System.out.println(r);
        System.out.println(t.params());
        System.out.println(t.stats());
        Trace.on = true;
        System.out.println("--- FIND B610505 (present) ---");
        t.get("B610505");
        System.out.println("--- FIND NOPE123 (missing) ---");
        t.get("NOPE123");
    }
}
