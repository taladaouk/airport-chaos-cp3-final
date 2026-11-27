package airport.sort;

import airport.cli.Trace;
import airport.model.Flight;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Part D — LSD (least-significant-digit first) radix sorts. Never calls a library sort.
 * (Arrays.copyOf below only allocates a same-typed array; it does not sort anything.)
 *
 *  byId(...)              character-based LSD radix on the flight_id STRING.
 *                         Gives exact lexicographic order — no numeric-encoding collisions.
 *                         Cost O(L * (n + 129)), L = longest ID length.
 *  sortStrings(...)       same algorithm on plain strings (airport codes for the route matrix).
 *  stableSortByStart(...) LSD radix on the long originalStart, base 256, 8 passes.
 *                         Cost O(8 * (n + 256)) = O(n).
 *
 * Why LSD works: we sort by the LEAST significant digit first and the MOST significant
 * last. Each pass is a STABLE counting sort, so when two items tie on the current digit,
 * the order from the earlier passes (the less significant digits) is kept. After the last
 * pass the items are ordered by the most significant digit, ties broken by the next one,
 * and so on — exactly lexicographic order. If a pass were not stable, it would scramble
 * the work of the previous passes and the result would be wrong.
 */
public final class RadixSort {
    private RadixSort() {}

    static final int CHAR_BUCKETS = 129;   // 0 = "no character at this position", 1..128 = ASCII 0..127

    /** Bucket of string s at position d. Past the end -> 0, so shorter strings come first ("AB" < "ABC"). */
    static int key(String s, int d) {
        if (d >= s.length()) return 0;
        char c = s.charAt(d);
        if (c > 127) throw new IllegalArgumentException("non-ASCII character in \"" + s + "\"");
        return c + 1;
    }

    /** Flights sorted by flight_id in exact lexicographic order. Returns a NEW array. */
    public static Flight[] byId(Flight[] a) {
        return lsdByString(a, f -> f.id, "radix byId");
    }

    /** Plain strings in lexicographic order (used for the airport-code list). Returns a NEW array. */
    public static String[] sortStrings(String[] a) {
        return lsdByString(a, s -> s, "radix strings");
    }

    /**
     * The LSD loop. Same algorithm as the plan's pseudocode, with one engineering change:
     * we read every key's characters ONCE into col[d][i] (column d = character position d)
     * and then sort an array of INDICES (perm) instead of the objects themselves.
     * Reason: on 1M flights, reading f.id.charAt(d) on every pass chases pointers through
     * ~100 MB of objects and misses the CPU cache; col[d] is a compact 1 MB byte array.
     * Each pass is still a stable counting sort on one character -> O(L * (n + 129)).
     */
    private static <T> T[] lsdByString(T[] input, Function<T, String> keyOf, String label) {
        int n = input.length;
        String[] keys = new String[n];
        int L = 0;
        for (int i = 0; i < n; i++) { keys[i] = keyOf.apply(input[i]); L = Math.max(L, keys[i].length()); }

        // col[d][i] = bucket of key i at character position d (0 = past the end, 1..128 = ASCII+1)
        byte[][] col = new byte[L][n];
        for (int i = 0; i < n; i++)
            for (int d = 0; d < L; d++) col[d][i] = (byte) key(keys[i], d);   // 128 is stored as -128; read back with & 0xFF

        int[] perm = new int[n], tmp = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;           // start in input order
        int[] count = new int[CHAR_BUCKETS];

        for (int d = L - 1; d >= 0; d--) {                // last character first
            byte[] c = col[d];
            Arrays.fill(count, 0);
            for (int i = 0; i < n; i++) count[c[i] & 0xFF]++;                    // 1) histogram
            for (int k = 1; k < CHAR_BUCKETS; k++) count[k] += count[k - 1];      // 2) prefix sums
            for (int i = n - 1; i >= 0; i--) {                                    // 3) place BACKWARDS = stable
                int p = perm[i];
                tmp[--count[c[p] & 0xFF]] = p;
            }
            int[] sw = perm; perm = tmp; tmp = sw;
            if (Trace.on) Trace.log(label + " pass on char position " + d + ": " + preview(keys, perm));
        }

        T[] out = Arrays.copyOf(input, n);   // same-typed array; every slot is overwritten below
        for (int i = 0; i < n; i++) out[i] = input[perm[i]];
        return out;
    }

    /**
     * Stable sort by originalStart (a long, minutes since epoch). For P3's crisis ordering.
     * 8 passes, one per byte, 256 buckets, placing backwards.
     *
     * We flip the sign bit (x ^ Long.MIN_VALUE) before taking bytes. That maps signed longs
     * onto unsigned order, so even a negative timestamp would sort correctly. For our
     * positive minute values it changes nothing in the result.
     */
    public static Flight[] stableSortByStart(Flight[] input) {
        int n = input.length;
        Flight[] a = Arrays.copyOf(input, n), aOut = new Flight[n];
        long[] k = new long[n], kOut = new long[n];      // keys travel WITH their flights (no pointer chasing)
        for (int i = 0; i < n; i++) k[i] = input[i].originalStart ^ Long.MIN_VALUE;
        int[] count = new int[256];

        for (int pass = 0; pass < 8; pass++) {           // byte 0 (least significant) first
            int shift = 8 * pass;
            Arrays.fill(count, 0);
            for (int i = 0; i < n; i++) count[(int) ((k[i] >>> shift) & 0xFF)]++;
            for (int b = 1; b < 256; b++) count[b] += count[b - 1];
            for (int i = n - 1; i >= 0; i--) {           // backwards = stable
                int pos = --count[(int) ((k[i] >>> shift) & 0xFF)];
                aOut[pos] = a[i];
                kOut[pos] = k[i];
            }
            Flight[] sa = a; a = aOut; aOut = sa;
            long[] sk = k; k = kOut; kOut = sk;
            if (Trace.on) Trace.log("radix byStart pass " + pass + " (byte " + pass + "): " + preview(a, f -> f.id + "@" + f.originalStart));
        }
        return a;
    }

    // ------------------------------------------------------------------ numeric-key variant
    /*
     * Handout Part D, literal reading: "stable LSD radix sort on a nonnegative integer key derived
     * deterministically from flight_id". byNumericKey does exactly that.
     *
     * IMPORTANT (label it this way in the CLI/report): the result is in NUMERIC-KEY order, which is
     * NOT alphabetical order. For alphabetical order use byId (character-based, exact).
     *
     * Collision-safe tie strategy: two different IDs can map to the same integer (the encoding is
     * taken mod a prime). We first sort by the full ID string with byId, then run the stable
     * numeric radix sort. Stability keeps equal-key (colliding) flights in ID order, so the output
     * is fully deterministic: by key, ties broken by the original ID string.
     */

    /** Modulus of the ID encoding: the prime 1,000,000,007 (keeps every key nonnegative and < 2^30). */
    public static final long ID_PRIME = 1_000_000_007L;
    /** Base of the polynomial encoding; larger than every ASCII code (0..127). */
    public static final long ID_BASE = 131L;

    /**
     * Deterministic nonnegative integer key of a flight_id:
     *   key = (c0*B^(L-1) + c1*B^(L-2) + ... + c(L-1)) mod P,  B = 131, P = 1,000,000,007   (Horner's rule)
     * Each step stays below P*B + 128 < 2^38, so long never overflows.
     * This is exactly P1's IdEncoder.encode (the same function the hash table uses).
     */
    public static long idKey(String id) {
        return airport.registry.IdEncoder.encode(id);   // P1's encoding: the project uses ONE documented encoding
    }

    /** byNumericKey with the default encoding idKey. Returns a NEW array. */
    public static Flight[] byNumericKey(Flight[] input) {
        return byNumericKey(input, f -> idKey(f.id));
    }

    /**
     * Stable LSD radix sort on a nonnegative long key, base 256 (one byte per pass, 256 buckets,
     * placing backwards = stable). Only as many passes as the largest key needs (4 for idKey).
     * Ties (encoding collisions) end up in exact flight_id order - see the comment above.
     * Cost O(n*L) for the id pre-sort + O(passes * (n + 256)). Returns a NEW array.
     */
    public static Flight[] byNumericKey(Flight[] input, java.util.function.ToLongFunction<Flight> keyFn) {
        int n = input.length;
        Flight[] a = byId(input);                    // collision-safe tie order: full ID string
        long[] k = new long[n];
        long max = 0;
        for (int i = 0; i < n; i++) {
            k[i] = keyFn.applyAsLong(a[i]);
            if (k[i] < 0) throw new IllegalArgumentException("numeric key must be nonnegative for " + a[i].id);
            if (k[i] > max) max = k[i];
        }
        int passes = 1;
        while (passes < 8 && (max >>> (8 * passes)) != 0) passes++;

        Flight[] aOut = new Flight[n];
        long[] kOut = new long[n];
        int[] count = new int[256];
        for (int pass = 0; pass < passes; pass++) {
            int shift = 8 * pass;
            Arrays.fill(count, 0);
            for (int i = 0; i < n; i++) count[(int) ((k[i] >>> shift) & 0xFF)]++;
            for (int b = 1; b < 256; b++) count[b] += count[b - 1];
            for (int i = n - 1; i >= 0; i--) {
                int pos = --count[(int) ((k[i] >>> shift) & 0xFF)];
                aOut[pos] = a[i];
                kOut[pos] = k[i];
            }
            Flight[] sa = a; a = aOut; aOut = sa;
            long[] sk = k; k = kOut; kOut = sk;
            if (Trace.on) {
                StringBuilder sb = new StringBuilder();
                int show = Math.min(n, 10);
                for (int i = 0; i < show; i++) { if (i > 0) sb.append(' '); sb.append(a[i].id).append('#').append(k[i]); }
                if (n > show) sb.append(" ... (+").append(n - show).append(')');
                Trace.log("radix numericKey pass " + pass + " (byte " + pass + "): " + sb);
            }
        }
        return a;
    }

    /**
     * The crisis recovery order (priority, originalStart, flight_id) built from three stable
     * sorts, least significant key FIRST: id, then start, then priority. This is LSD radix
     * thinking applied to whole keys instead of digits.
     */
    public static Flight[] crisisOrder(Flight[] affected) {
        Flight[] a = byId(affected);
        a = stableSortByStart(a);
        return CountingSort.byPriority(a);
    }

    private static String preview(String[] keys, int[] perm) {
        StringBuilder sb = new StringBuilder();
        int show = Math.min(perm.length, 10);
        for (int i = 0; i < show; i++) { if (i > 0) sb.append(' '); sb.append(keys[perm[i]]); }
        if (perm.length > show) sb.append(" ... (+").append(perm.length - show).append(')');
        return sb.toString();
    }

    private static <T> String preview(T[] a, Function<T, String> keyOf) {
        StringBuilder sb = new StringBuilder();
        int show = Math.min(a.length, 10);
        for (int i = 0; i < show; i++) { if (i > 0) sb.append(' '); sb.append(keyOf.apply(a[i])); }
        if (a.length > show) sb.append(" ... (+").append(a.length - show).append(')');
        return sb.toString();
    }
}
