package airport;

import airport.io.CsvLoader;
import airport.io.LoadResult;
import airport.registry.FlightHashTable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvLoaderTest {
    private static final String H =
        "flight_id,airline,origin,destination,runway,start_time,end_time,priority,status";

    @Test
    void duplicateIdIsReportedAndFirstKept() {
        List<String> lines = Arrays.asList(H,
            "A1,AA,JFK,BOS,R1,2026-11-27 09:00,2026-11-27 09:05,1,SCHEDULED",
            "A2,AA,JFK,BOS,R1,2026-11-27 09:10,2026-11-27 09:15,2,SCHEDULED",
            "A1,AA,JFK,BOS,R2,2026-11-27 10:00,2026-11-27 10:05,3,SCHEDULED");
        FlightHashTable t = new FlightHashTable();
        LoadResult r = CsvLoader.loadLines(lines, t);
        assertEquals(2, r.loaded);
        assertEquals(2, r.flights.size());
        assertEquals(1, r.errors.size());
        assertTrue(r.errors.get(0).contains("duplicate"));
        assertTrue(r.errors.get(0).contains("A1"));
        assertEquals("R1", t.get("A1").runway);   // first one kept
    }

    @Test
    void malformedRowsAreRejectedWithLineNumbers() {
        List<String> lines = Arrays.asList(H,
            "A1,AA,JFK,BOS,R1,2026-13-01 09:00,2026-11-27 09:05,1,SCHEDULED",   // line 2 bad month
            "A2,AA,JFK,BOS,R1,2026-11-27 9:0,2026-11-27 09:05,1,SCHEDULED",     // line 3 bad format
            "A3,AA,JFK,BOS,R1,2026-11-27 09:10,2026-11-27 09:10,1,SCHEDULED",   // line 4 start>=end
            "A4,AA,JFK,BOS,R1,2026-11-27 09:10,2026-11-27 09:15,1");            // line 5 too few fields
        FlightHashTable t = new FlightHashTable();
        LoadResult r = CsvLoader.loadLines(lines, t);
        assertEquals(0, r.loaded);
        assertEquals(4, r.errors.size());
        assertTrue(r.errors.get(0).contains("line 2"));
        assertTrue(r.errors.get(1).contains("line 3"));
        assertTrue(r.errors.get(2).contains("line 4"));
        assertTrue(r.errors.get(3).contains("line 5"));
    }

    @Test
    void officialFileLoads1000FlightsWithNoErrors() {
        FlightHashTable t = new FlightHashTable();
        LoadResult r = CsvLoader.load("data/airport_flights.csv", t);
        assertEquals(1000, r.loaded);
        assertEquals(0, r.errors.size());
        assertEquals(1000, t.size());
        assertNotNull(t.get("B610505"));
    }

    @Test
    void oneArgumentLoadFillsFlightsList() {
        LoadResult r = CsvLoader.load("data/airport_flights.csv");
        assertEquals(1000, r.flights.size());
    }
}
