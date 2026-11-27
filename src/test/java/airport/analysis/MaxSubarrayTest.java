package airport.analysis;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Test 15: divide and conquer equals brute force. */
class MaxSubarrayTest {

    @Test
    void dcEqualsBruteOnRandomArrays() {
        Random rnd = new Random(7);
        for (int k = 0; k < 200; k++) {
            long[] a = new long[1 + rnd.nextInt(60)];
            for (int i = 0; i < a.length; i++) a[i] = rnd.nextInt(5);   // non-negative loads, many ties
            MaxSubarray.Result dc = MaxSubarray.divideAndConquer(a);
            MaxSubarray.Result br = MaxSubarray.brute(a);
            assertTrue(dc.sameAs(br), "DC " + dc + " vs brute " + br);
        }
    }

    @Test
    void dcEqualsBruteWithNegatives() {
        Random rnd = new Random(11);
        for (int k = 0; k < 200; k++) {
            long[] a = new long[1 + rnd.nextInt(40)];
            for (int i = 0; i < a.length; i++) a[i] = rnd.nextInt(11) - 5;
            assertTrue(MaxSubarray.divideAndConquer(a).sameAs(MaxSubarray.brute(a)));
        }
    }

    @Test
    void allZerosReturnsFirstCell() {
        long[] a = new long[288];
        MaxSubarray.Result dc = MaxSubarray.divideAndConquer(a);
        MaxSubarray.Result br = MaxSubarray.brute(a);
        assertEquals(0, dc.lo); assertEquals(0, dc.hi); assertEquals(0, dc.sum);
        assertTrue(dc.sameAs(br));
    }

    @Test
    void tieRuleShortestThenEarliest() {
        long[] a = {3, 0, 0, 3};      // sums: [0,0]=3, [3,3]=3, [0,3]=6 -> whole range wins
        assertEquals(6, MaxSubarray.divideAndConquer(a).sum);
        long[] b = {3, -5, 3};        // two equal best cells -> earlier one
        MaxSubarray.Result r = MaxSubarray.divideAndConquer(b);
        assertEquals(0, r.lo); assertEquals(0, r.hi);
        long[] c = {0, 0, 2, 0, 1, 0, 0};   // zero padding is trimmed: [2,4], not [0,4] or [0,6]
        MaxSubarray.Result rc = MaxSubarray.divideAndConquer(c);
        assertEquals(2, rc.lo); assertEquals(4, rc.hi); assertEquals(3, rc.sum);
        assertTrue(rc.sameAs(MaxSubarray.brute(c)));
    }
}
