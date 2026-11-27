package airport.sort;

import airport.bench.SyntheticData;
import airport.model.Flight;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Test 14 — sorting stability (owner P4). */
class SortingStabilityTest {

    static Flight f(String id, int prio, long start) {
        return new Flight(id, "XX", "AAA", "BBB", "R1", start, start + 5, prio);
    }

    @Test
    void countingSortKeepsEarlierOrderForEqualPriorities() {
        // Input already in ID order; equal priorities must stay in that order.
        Flight[] in = { f("A1", 2, 0), f("A2", 1, 0), f("A3", 3, 0), f("A4", 2, 0), f("A5", 1, 0), f("A6", 3, 0) };
        Flight[] out = CountingSort.byPriority(in);
        String[] expected = { "A2", "A5", "A1", "A4", "A3", "A6" };
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], out[i].id);
        assertEquals("A1", in[0].id, "input array must not be modified");
    }

    @Test
    void countingSortStableOnLargeRandomInput() {
        Flight[] in = SyntheticData.flights(5_000, new Random(7));
        Flight[] out = CountingSort.byPriority(in);
        // record each flight's original index by scanning (no HashMap)
        for (int i = 1; i < out.length; i++) {
            assertTrue(out[i - 1].priority <= out[i].priority, "not sorted by priority");
            if (out[i - 1].priority == out[i].priority)
                assertTrue(indexOf(in, out[i - 1]) < indexOf(in, out[i]), "equal priorities changed order");
        }
        assertEquals(in.length, out.length);
    }

    private static int indexOf(Flight[] a, Flight x) {
        for (int i = 0; i < a.length; i++) if (a[i] == x) return i;
        return -1;
    }

    @Test
    void radixByIdEqualsLexicographicOrder() {
        Flight[] in = {
            f("B610505", 1, 0), f("AB", 1, 0), f("ABC", 1, 0), f("A", 1, 0), f("b1", 1, 0),
            f("B6", 1, 0), f("Z9", 1, 0), f("AA0999", 1, 0), f("B610504", 1, 0), f("9X", 1, 0)
        };
        Flight[] out = RadixSort.byId(in);
        for (int i = 1; i < out.length; i++)
            assertTrue(out[i - 1].id.compareTo(out[i].id) < 0, out[i - 1].id + " should come before " + out[i].id);
        // shorter prefix first, digits before upper-case before lower-case (ASCII)
        assertEquals("9X", out[0].id);
        assertEquals("A", out[1].id);
        assertEquals("AA0999", out[2].id);
        assertEquals("AB", out[3].id);
        assertEquals("ABC", out[4].id);
        assertEquals("b1", out[out.length - 1].id);
    }

    @Test
    void radixByIdMatchesOurMergeSortOnRandomData() {
        Flight[] in = SyntheticData.flights(20_000, new Random(11));
        Flight[] r = RadixSort.byId(in), m = MergeSort.byId(in);
        for (int i = 0; i < in.length; i++) assertSame(m[i], r[i]);
    }

    @Test
    void stableSortByStartIsSortedAndStable() {
        Flight[] in = { f("X3", 1, 600), f("X1", 1, 300), f("X2", 1, 600), f("X0", 1, 0), f("X4", 1, 300) };
        Flight[] out = RadixSort.stableSortByStart(in);
        String[] expected = { "X0", "X1", "X4", "X3", "X2" };   // ties 300 and 600 keep input order
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], out[i].id);
    }

    @Test
    void stableSortByStartHandlesLargeAndNegativeValues() {
        Flight[] in = { f("a", 1, Long.MAX_VALUE - 1), f("b", 1, -5), f("c", 1, 0), f("d", 1, 1L << 40), f("e", 1, Long.MIN_VALUE) };
        Flight[] out = RadixSort.stableSortByStart(in);
        String[] expected = { "e", "b", "c", "d", "a" };
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], out[i].id);
    }

    @Test
    void crisisOrderIsPriorityThenStartThenId() {
        Flight[] in = {
            f("C", 3, 100), f("B", 1, 200), f("A", 1, 200), f("D", 1, 100), f("E", 2, 50), f("F", 3, 100)
        };
        Flight[] out = RadixSort.crisisOrder(in);
        String[] expected = { "D", "A", "B", "E", "C", "F" };
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], out[i].id);
    }

    @Test
    void emptyAndSingleInputs() {
        assertEquals(0, CountingSort.byPriority(new Flight[0]).length);
        assertEquals(0, RadixSort.byId(new Flight[0]).length);
        assertEquals(0, RadixSort.stableSortByStart(new Flight[0]).length);
        Flight[] one = { f("Q1", 2, 5) };
        assertSame(one[0], RadixSort.byId(one)[0]);
        assertSame(one[0], CountingSort.byPriority(one)[0]);
    }

    @Test
    void numericKeyRadixIsSortedByKeyWithIdTieBreak() {
        Flight[] in = SyntheticData.flights(20_000, new Random(5));
        Flight[] out = RadixSort.byNumericKey(in);
        assertEquals(in.length, out.length);
        for (int i = 1; i < out.length; i++) {
            long a = RadixSort.idKey(out[i - 1].id), b = RadixSort.idKey(out[i].id);
            assertTrue(a <= b, "not in numeric-key order at " + i);
            if (a == b) assertTrue(out[i - 1].id.compareTo(out[i].id) < 0, "collision not broken by ID");
        }
    }

    @Test
    void numericKeyCollisionsAreBrokenByFullIdString() {
        // Force collisions: key = ID length. "C","A","B" all collide on key 1 and must come out A, B, C.
        Flight[] in = { f("CC", 1, 0), f("C", 1, 0), f("A", 1, 0), f("BB", 1, 0), f("B", 1, 0), f("AAA", 1, 0) };
        Flight[] out = RadixSort.byNumericKey(in, x -> x.id.length());
        String[] expected = { "A", "B", "C", "BB", "CC", "AAA" };
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], out[i].id);
    }

    @Test
    void idKeyIsDeterministicAndNonnegative() {
        assertEquals(RadixSort.idKey("B610505"), RadixSort.idKey("B610505"));
        assertEquals(0, RadixSort.idKey(""));
        assertEquals('A', RadixSort.idKey("A"));
        assertEquals('A' * 131L + 'B', RadixSort.idKey("AB"));
        long k = RadixSort.idKey("ZZZZZZZZZZZZZZZZZZZZZZZZ");          // long ID: reduced mod P
        assertTrue(k >= 0 && k < RadixSort.ID_PRIME);
        assertThrows(IllegalArgumentException.class, () -> RadixSort.byNumericKey(new Flight[] { f("A", 1, 0) }, x -> -1));
        assertEquals(0, RadixSort.byNumericKey(new Flight[0]).length);
    }

    @Test
    void nonAsciiIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RadixSort.byId(new Flight[] { f("Ä1", 1, 0), f("A1", 1, 0) }));
    }
}
