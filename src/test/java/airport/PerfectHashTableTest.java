package airport;

import airport.perfect.PerfectHashTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerfectHashTableTest {
    @Test
    void allKeysFoundAndAbsentKeysNot() {
        PerfectHashTable t = new PerfectHashTable(PerfectHashTable.RULE_IDS, 7);
        for (int k : PerfectHashTable.RULE_IDS) assertTrue(t.contains(k), "should contain " + k);
        int[] absent = {100, 150, 999, 0, 284};
        for (int k : absent) assertFalse(t.contains(k), "should not contain " + k);
    }

    @Test
    void noLevelTwoCollisions() {
        PerfectHashTable t = new PerfectHashTable(PerfectHashTable.RULE_IDS, 7);
        assertEquals(20, t.storedCount());
        assertTrue(t.totalStorage() <= 20 + 80);
    }
}
