package airport.bench;

import airport.model.Flight;
import java.util.Random;

/**
 * Synthetic data for the scale experiments. Everything uses new Random(2026) so every run
 * produces the same data. The official CSV stays the correctness baseline; these are only
 * for timing.
 */
public final class SyntheticData {
    private SyntheticData() {}

    public static final long SEED = 2026;
    static final String[] AIRLINES = { "AA", "B6", "DL", "UA", "WN", "AS", "NK", "F9" };
    static final String[] AIRPORTS = { "ATL", "BOS", "DEN", "DFW", "JFK", "LAX", "MIA", "ORD", "SEA", "SFO" };
    static final long BASE = 29_400_000L; // ~ 2025-11 in minutes since epoch; any positive value works

    /**
     * 2n DISTINCT 6-digit numbers, shuffled. The first n become "hit" IDs, the next n "miss" IDs,
     * so misses are guaranteed absent from the table — without needing a HashSet.
     * (Partial Fisher–Yates on 0..999,999.)
     */
    static int[] distinctNumbers(int count, Random rnd) {
        int space = 1_000_000;
        if (count > space) throw new IllegalArgumentException("too many IDs for 6 digits");
        int[] pool = new int[space];
        for (int i = 0; i < space; i++) pool[i] = i;
        for (int i = 0; i < count; i++) {
            int j = i + rnd.nextInt(space - i);
            int t = pool[i]; pool[i] = pool[j]; pool[j] = t;
        }
        int[] out = new int[count];
        System.arraycopy(pool, 0, out, 0, count);
        return out;
    }

    /** n flights with unique IDs like "B6123456", random priority 1..3, random start. */
    public static Flight[] flights(int n, Random rnd) {
        int[] nums = n <= 1_000_000 ? distinctNumbers(n, rnd) : null;
        Flight[] fs = new Flight[n];
        for (int i = 0; i < n; i++) {
            String al = AIRLINES[rnd.nextInt(AIRLINES.length)];
            // above 1M we add a counter suffix to keep IDs unique
            String id = nums != null ? al + String.format("%06d", nums[i]) : al + i;
            fs[i] = make(id, al, rnd);
        }
        return fs;
    }

    /** n hit flights + n miss IDs that are guaranteed not to be in the hits. */
    public static Object[] hitsAndMisses(int n, Random rnd) {
        int[] nums = distinctNumbers(2 * n, rnd);
        Flight[] hits = new Flight[n];
        String[] misses = new String[n];
        for (int i = 0; i < n; i++) {
            String al = AIRLINES[rnd.nextInt(AIRLINES.length)];
            hits[i] = make(al + String.format("%06d", nums[i]), al, rnd);
            misses[i] = AIRLINES[rnd.nextInt(AIRLINES.length)] + "X" + String.format("%06d", nums[n + i]);
        }
        return new Object[] { hits, misses };
    }

    private static Flight make(String id, String al, Random rnd) {
        String o = AIRPORTS[rnd.nextInt(AIRPORTS.length)], d;
        do { d = AIRPORTS[rnd.nextInt(AIRPORTS.length)]; } while (d.equals(o));
        long s = BASE + 5L * rnd.nextInt(288 * 30);
        return new Flight(id, al, o, d, "R" + (1 + rnd.nextInt(5)), s, s + 5, 1 + rnd.nextInt(3));
    }

    /** Distinct BST keys 0, 5, 10, ... in sorted order (worst case for an unbalanced BST). */
    public static long[] sortedKeys(int n) {
        long[] k = new long[n];
        for (int i = 0; i < n; i++) k[i] = BASE + 5L * i;
        return k;
    }

    /** The same keys shuffled with Fisher–Yates (random insertion order -> expected height O(log n)). */
    public static long[] shuffled(long[] keys, Random rnd) {
        long[] k = keys.clone();
        for (int i = k.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            long t = k[i]; k[i] = k[j]; k[j] = t;
        }
        return k;
    }
}
