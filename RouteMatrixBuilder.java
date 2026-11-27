package airport.matrix;

import airport.model.Flight;
import airport.sort.RadixSort;
import java.util.ArrayList;
import java.util.List;

/**
 * Part F — the origin-to-destination FLIGHT-COUNT matrix.
 *
 *  1. Collect every origin and destination code, sort with OUR radix sort, remove duplicates
 *     -> the airport list in lexicographic order (size N).
 *  2. Airport -> index by BINARY SEARCH on that sorted array (no HashMap): O(log N).
 *  3. A[i][j] = number of flights from airport i to airport j, over ALL flights in the loaded
 *     file (whatever their status) — stated in the report.
 *  4. A x A: entry (i, j) = sum over k of A[i][k] * A[k][j] = number of ways to go
 *     i -> k -> j using two flights. This is a "two-leg route-count proxy": it is NOT
 *     passenger flow and NOT a guaranteed connection (times are ignored).
 */
public final class RouteMatrixBuilder {

    public final String[] airports;   // sorted, unique
    public final long[][] A;          // N x N flight counts

    private RouteMatrixBuilder(String[] airports, long[][] A) { this.airports = airports; this.A = A; }

    public static RouteMatrixBuilder build(Flight[] flights) {
        // 1) all codes (2 per flight), sorted with our own radix sort
        String[] codes = new String[2 * flights.length];
        for (int i = 0; i < flights.length; i++) {
            codes[2 * i] = flights[i].origin;
            codes[2 * i + 1] = flights[i].destination;
        }
        String[] sorted = RadixSort.sortStrings(codes);

        // remove duplicates: in a sorted array equal codes are adjacent
        int u = 0;
        for (int i = 0; i < sorted.length; i++)
            if (i == 0 || !sorted[i].equals(sorted[i - 1])) sorted[u++] = sorted[i];
        String[] airports = new String[u];
        System.arraycopy(sorted, 0, airports, 0, u);

        // 3) count flights i -> j
        long[][] A = new long[u][u];
        for (Flight f : flights) A[indexOf(airports, f.origin)][indexOf(airports, f.destination)]++;
        return new RouteMatrixBuilder(airports, A);
    }

    /** 2) binary search; -1 if not found. */
    public static int indexOf(String[] sortedAirports, String code) {
        int lo = 0, hi = sortedAirports.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = sortedAirports[mid].compareTo(code);
            if (c == 0) return mid;
            if (c < 0) lo = mid + 1; else hi = mid - 1;
        }
        return -1;
    }

    public int indexOf(String code) { return indexOf(airports, code); }

    /** A x A via Strassen (default cutoff); the test suite checks it equals classical. */
    public long[][] twoLeg() { return Strassen.multiply(A, A); }

    /** One (origin, destination, count) entry of the top-k list. */
    public static final class Pair {
        public final int i, j; public final long count;
        Pair(int i, int j, long count) { this.i = i; this.j = j; this.count = count; }
    }

    /**
     * Top k pairs of M by count (largest first; ties -> smaller i, then smaller j).
     * The diagonal (i -> k -> i round trips) is skipped unless includeDiagonal.
     * Uses a small insertion-ordered array of size k — no library sort. O(N^2 * k).
     */
    public static List<Pair> topK(long[][] M, int k, boolean includeDiagonal) {
        Pair[] best = new Pair[k];
        int filled = 0;
        for (int i = 0; i < M.length; i++) {
            for (int j = 0; j < M.length; j++) {
                if (!includeDiagonal && i == j) continue;
                if (M[i][j] == 0) continue;
                Pair p = new Pair(i, j, M[i][j]);
                if (filled < k) best[filled++] = p;
                else if (better(p, best[k - 1])) best[k - 1] = p;
                else continue;
                // bubble the new entry up to its place (insertion step)
                for (int t = Math.min(filled, k) - 1; t > 0 && better(best[t], best[t - 1]); t--) {
                    Pair tmp = best[t]; best[t] = best[t - 1]; best[t - 1] = tmp;
                }
            }
        }
        List<Pair> out = new ArrayList<>();
        for (int t = 0; t < filled; t++) out.add(best[t]);
        return out;
    }

    private static boolean better(Pair a, Pair b) {
        if (a.count != b.count) return a.count > b.count;
        if (a.i != b.i) return a.i < b.i;
        return a.j < b.j;
    }

    /** Text block for the ROUTES / report output. */
    public String report(int k) {
        long[][] A2 = twoLeg();
        StringBuilder sb = new StringBuilder();
        sb.append("Route matrix: N = ").append(airports.length).append(" airports (lexicographic order), built from ALL flights in the loaded file\n");
        sb.append("Top ").append(k).append(" of A x A  -  two-leg route-count proxy (not passenger flow, not guaranteed connections)\n");
        sb.append(String.format("  %-4s %-6s %-6s %s%n", "#", "from", "to", "two-leg routes"));
        int r = 1;
        for (Pair p : topK(A2, k, false))
            sb.append(String.format("  %-4d %-6s %-6s %d%n", r++, airports[p.i], airports[p.j], p.count));
        return sb.toString();
    }
}
