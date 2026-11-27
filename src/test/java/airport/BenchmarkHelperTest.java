package airport;

import airport.registry.FlightHashTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkHelperTest {
    @Test
    void buildsTableAtRequestedLoadFactor() {
        FlightHashTable t = FlightHashTable.buildForBenchmark(1000, 0.5);
        assertEquals(1000, t.size());
        assertEquals(0, t.stats().resizes);          // resizing is off
        assertEquals(0.5, t.loadFactor(), 0.01);
        assertNotNull(t.get("BM500"));
        t.resetStats();
        assertEquals(0, t.stats().totalProbes);
    }
}
