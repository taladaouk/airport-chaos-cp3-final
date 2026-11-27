package airport.perfect;

import java.util.Random;

/**
 * Static two-level FKS perfect hashing for a FIXED set of distinct integer keys.
 * Level 1: h(x) = ((a*x + b) mod p) mod m, with p = 1009 (prime > every key), m = n.
 *          Redraw (a,b) until sum of (bucket size)^2 <= 4n.
 * Level 2: each bucket of size s gets a table of size s^2 with its own (a_i, b_i),
 *          redrawn until the bucket's keys land in different slots.
 * contains(x) = 2 hash computations + 1 comparison.
 */
public class PerfectHashTable {
    public static final int[] RULE_IDS = {101, 107, 113, 127, 131, 149, 157, 163, 173, 181,
                                          193, 211, 223, 227, 239, 251, 263, 269, 277, 283};
    private static final long P = 1009L;
    private static final int EMPTY = -1;

    private final int n, m;
    private final long a1, b1;
    private final int level1Attempts;
    private final int[] bucketSize;
    private final long[] a2, b2;
    private final int[] attempts2;
    private final int[][] table;

    public PerfectHashTable(int[] keys, long seed) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] < 0 || keys[i] >= P) throw new IllegalArgumentException("key out of range: " + keys[i]);
            for (int j = i + 1; j < keys.length; j++) {
                if (keys[i] == keys[j]) throw new IllegalArgumentException("duplicate key: " + keys[i]);
            }
        }
        n = keys.length;
        m = n;
        Random rnd = new Random(seed);

        // ---- Level 1 ----
        long ta, tb;
        int[] counts;
        int attempts = 0;
        while (true) {
            attempts++;
            ta = 1 + rnd.nextInt((int) (P - 1));   // 1 .. p-1
            tb = rnd.nextInt((int) P);             // 0 .. p-1
            counts = new int[m];
            for (int x : keys) counts[h(ta, tb, x, m)]++;
            long sumSq = 0;
            for (int c : counts) sumSq += (long) c * c;
            if (sumSq <= 4L * n) break;
        }
        a1 = ta;
        b1 = tb;
        level1Attempts = attempts;
        bucketSize = counts;

        // ---- Level 2 ----
        a2 = new long[m];
        b2 = new long[m];
        attempts2 = new int[m];
        table = new int[m][];
        for (int j = 0; j < m; j++) {
            int s = bucketSize[j];
            if (s == 0) continue;                  // no table for an empty bucket
            int size = s * s;
            while (true) {
                attempts2[j]++;
                long a = 1 + rnd.nextInt((int) (P - 1));
                long b = rnd.nextInt((int) P);
                int[] t = new int[size];
                java.util.Arrays.fill(t, EMPTY);
                boolean ok = true;
                for (int x : keys) {
                    if (h(a1, b1, x, m) != j) continue;
                    int slot = h(a, b, x, size);
                    if (t[slot] != EMPTY) { ok = false; break; }
                    t[slot] = x;
                }
                if (ok) {
                    a2[j] = a;
                    b2[j] = b;
                    table[j] = t;
                    break;
                }
            }
        }
    }

    private static int h(long a, long b, long x, int size) {
        return (int) (Math.floorMod(a * x + b, P) % size);
    }

    public boolean contains(int x) {
        int j = h(a1, b1, x, m);
        if (table[j] == null) return false;
        int slot = h(a2[j], b2[j], x, table[j].length);
        return table[j][slot] == x;
    }

    /** Number of keys actually stored in level-2 tables (equals n only if there were no collisions). */
    public int storedCount() {
        int c = 0;
        for (int[] t : table) {
            if (t == null) continue;
            for (int v : t) if (v != EMPTY) c++;
        }
        return c;
    }

    /** Total storage = m level-1 slots + sum of s^2 level-2 slots. */
    public int totalStorage() {
        int total = m;
        for (int[] t : table) if (t != null) total += t.length;
        return total;
    }

    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FKS perfect hashing: n=%d, m=%d, p=%d%n", n, m, P));
        sb.append(String.format("Level 1: a=%d, b=%d, attempts=%d%n", a1, b1, level1Attempts));
        long sumSq = 0;
        for (int j = 0; j < m; j++) {
            int s = bucketSize[j];
            if (s == 0) continue;
            sumSq += (long) s * s;
            sb.append(String.format("  bucket %2d: size=%d, table size=%d, a=%d, b=%d, attempts=%d%n",
                    j, s, s * s, a2[j], b2[j], attempts2[j]));
        }
        sb.append(String.format("Sum of s^2 = %d (limit 4n = %d)%n", sumSq, 4 * n));
        sb.append(String.format("Total storage = %d level-1 slots + %d level-2 slots = %d%n",
                m, totalStorage() - m, totalStorage()));
        return sb.toString();
    }

    /** Demo you can run directly. */
    public static void main(String[] args) {
        PerfectHashTable t = new PerfectHashTable(RULE_IDS, 7);
        System.out.print(t.report());
        int[] present = {101, 211, 283};
        int[] absent = {100, 150, 999};
        for (int x : present) System.out.println("contains(" + x + ") = " + t.contains(x));
        for (int x : absent) System.out.println("contains(" + x + ") = " + t.contains(x));
    }
}
