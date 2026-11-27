package airport;

import airport.model.Flight;
import airport.registry.FlightHashTable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightHashTableTest {
    private Flight flight(int i) {
        return new Flight("F" + i, "AA", "JFK", "BOS", "R1", 1000 + i * 5L, 1005 + i * 5L, 1);
    }

    @Test
    void insertGetAndDuplicate() {
        FlightHashTable t = new FlightHashTable();
        assertTrue(t.insert(flight(1)));
        assertFalse(t.insert(flight(1)));
        assertEquals("F1", t.get("F1").id);
        assertNull(t.get("MISSING"));
        assertEquals(1, t.size());
    }

    /** Test 3 from the plan: A, B, C in ONE probe chain; remove B; C is still found; resize keeps all keys. */
    @Test
    void sameChainTombstoneThenResize() {
        FlightHashTable t = new FlightHashTable(11, false);          // small fixed table, no resize
        // find three IDs with the same home slot (a real collision chain), deterministically
        String[] chain = new String[3];
        int home = t.homeSlot("C0"), found = 0;
        for (int i = 0; found < 3; i++) {
            String id = "C" + i;
            if (t.homeSlot(id) == home) chain[found++] = id;
        }
        for (String id : chain) assertTrue(t.insert(new Flight(id, "AA", "JFK", "BOS", "R1", 0, 5, 1)));
        assertEquals(2, t.stats().collisions, "B and C both found their home slot taken");

        assertTrue(t.remove(chain[1]));                              // B -> TOMBSTONE in the middle of the chain
        assertEquals(1, t.stats().tombstones);
        assertNull(t.get(chain[1]));
        assertNotNull(t.get(chain[2]), "C must be reachable across B's tombstone");
        assertNotNull(t.get(chain[0]));

        // re-inserting B reuses the tombstone instead of extending the chain
        assertTrue(t.insert(new Flight(chain[1], "AA", "JFK", "BOS", "R1", 0, 5, 1)));
        assertEquals(0, t.stats().tombstones);

        // a resizing table: grows past many keys, all keys still found, tombstones cleared
        FlightHashTable r = new FlightHashTable();
        for (int i = 0; i < 50; i++) r.insert(new Flight("K" + i, "AA", "JFK", "BOS", "R1", 0, 5, 1));
        for (int i = 0; i < 50; i += 3) assertTrue(r.remove("K" + i));
        for (int i = 50; i < 200; i++) r.insert(new Flight("K" + i, "AA", "JFK", "BOS", "R1", 0, 5, 1));
        assertTrue(r.stats().resizes > 0);
        for (int i = 0; i < 200; i++) {
            if (i < 50 && i % 3 == 0) assertNull(r.get("K" + i));
            else assertNotNull(r.get("K" + i), "K" + i);
        }
        assertTrue((double) (r.size() + r.stats().tombstones) / r.capacity() <= 0.5);
    }

    @Test
    void tombstonesAndResize() {
        FlightHashTable t = new FlightHashTable();
        for (int i = 0; i < 200; i++) assertTrue(t.insert(flight(i)));
        assertTrue(t.stats().resizes > 0);
        for (int i = 0; i < 200; i += 2) assertTrue(t.remove("F" + i));
        for (int i = 0; i < 200; i++) {
            if (i % 2 == 0) assertNull(t.get("F" + i));
            else assertNotNull(t.get("F" + i));
        }
        assertFalse(t.remove("F0"));
        assertEquals(100, t.size());
        assertEquals(100, t.all().size());
        assertTrue(t.insert(flight(0)));
        assertNotNull(t.get("F0"));
    }
}
