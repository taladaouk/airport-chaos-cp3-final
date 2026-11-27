package airport.matrix;

import airport.model.Flight;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Test 17 — Strassen on odd (and even) sizes equals classical exactly (owner P4). */
class StrassenOddSizesTest {

    @Test
    void oddSizesEqualClassical() {
        for (int n : new int[] { 1, 3, 5, 7, 33 }) checkSize(n);
    }

    @Test
    void fullCorrectnessListFromThePlan() {
        for (int n : new int[] { 1, 2, 3, 5, 7, 8, 15, 16, 33 }) checkSize(n);
    }

    private void checkSize(int n) {
        Random rnd = new Random(2026L + n);
        long[][] A = Matrix.random(n, 9, rnd), B = Matrix.random(n, 9, rnd);
        long[][] C = Matrix.multiplyClassical(A, B);
        for (int cutoff : new int[] { 1, 2, 64 })
            assertTrue(Matrix.equal(C, Strassen.multiply(A, B, cutoff)), "N=" + n + " cutoff=" + cutoff);
        // result must be cropped back to N x N
        long[][] S = Strassen.multiply(A, B, 1);
        assertEquals(n, S.length);
        assertEquals(n, S[0].length);
    }

    @Test
    void handCheckedTwoByTwo() {
        long[][] A = { { 1, 2 }, { 3, 4 } }, B = { { 5, 6 }, { 7, 8 } };
        long[][] expected = { { 19, 22 }, { 43, 50 } };
        assertTrue(Matrix.equal(expected, Strassen.multiply(A, B, 1)));
        assertTrue(Matrix.equal(expected, Matrix.multiplyClassical(A, B)));
    }

    @Test
    void invalidInputsRejected() {
        long[][] A = { { 1, 2 }, { 3, 4 } };
        assertThrows(IllegalArgumentException.class, () -> Strassen.multiply(A, A, 0));
        assertThrows(IllegalArgumentException.class, () -> Strassen.multiply(A, new long[][] { { 1 } }, 1));
        assertEquals(0, Strassen.multiply(new long[0][0], new long[0][0], 1).length);
    }

    @Test
    void routeMatrixCountsAndTwoLegProxy() {
        Flight[] fs = {
            fl("1", "JFK", "BOS"), fl("2", "JFK", "BOS"), fl("3", "BOS", "ORD"),
            fl("4", "ORD", "JFK"), fl("5", "ATL", "JFK")
        };
        RouteMatrixBuilder rm = RouteMatrixBuilder.build(fs);
        assertArrayEquals(new String[] { "ATL", "BOS", "JFK", "ORD" }, rm.airports);
        assertEquals(2, rm.A[rm.indexOf("JFK")][rm.indexOf("BOS")]);
        assertEquals(-1, rm.indexOf("LAX"));
        long[][] A2 = rm.twoLeg();
        assertTrue(Matrix.equal(Matrix.multiplyClassical(rm.A, rm.A), A2));
        // JFK -> BOS -> ORD: 2 ways (two JFK->BOS flights x one BOS->ORD)
        assertEquals(2, A2[rm.indexOf("JFK")][rm.indexOf("ORD")]);
        // Three pairs tie at 2 (ATL->BOS, JFK->ORD, ORD->BOS); the tie rule is smaller i, then smaller j.
        List<RouteMatrixBuilder.Pair> top = RouteMatrixBuilder.topK(A2, 10, false);
        assertEquals(rm.indexOf("ATL"), top.get(0).i);
        assertEquals(rm.indexOf("BOS"), top.get(0).j);
        assertEquals(rm.indexOf("JFK"), top.get(1).i);
        assertEquals(rm.indexOf("ORD"), top.get(1).j);
        assertEquals(1, top.get(3).count);
    }

    private static Flight fl(String id, String o, String d) { return new Flight(id, "XX", o, d, "R1", 0, 5, 1); }
}
