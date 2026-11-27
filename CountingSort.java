package airport.sort;

import airport.cli.Trace;
import airport.model.Flight;

/**
 * Part D — stable counting sort on priority.
 *
 * Priorities are small integers (1..3 in the official schema), so we can use the key
 * itself as an array index instead of comparing flights. Cost O(n + k), k = max key.
 *
 * Stability: we place elements walking the input BACKWARDS. The prefix sum count[p]
 * is "one past the last slot for key p", so the last flight with key p seen in the
 * input goes into the last slot, the one before it into the slot before, and so on —
 * equal keys keep their original relative order.
 *
 * Never calls a library sort.
 */
public final class CountingSort {
    private CountingSort() {}

    /** Returns a NEW array sorted by priority ascending (1 first). Input is not modified. */
    public static Flight[] byPriority(Flight[] in) {
        int n = in.length;
        if (n == 0) return new Flight[0];

        // k = largest key. The schema says 1..3, but we read it from the data so a
        // change like "priorities 1..5" needs no code edit.
        int k = 0;
        for (Flight f : in) {
            if (f.priority < 0) throw new IllegalArgumentException("negative priority on " + f.id);
            if (f.priority > k) k = f.priority;
        }

        // 1) histogram: count[p] = how many flights have priority p
        int[] count = new int[k + 1];
        for (Flight f : in) count[f.priority]++;
        if (Trace.on) Trace.log("counting sort  count  = " + show(count));

        // 2) prefix sums: count[p] = number of flights with priority <= p
        //    = one past the last output index for key p
        for (int p = 1; p <= k; p++) count[p] += count[p - 1];
        if (Trace.on) Trace.log("counting sort  prefix = " + show(count));

        // 3) place, iterating BACKWARDS -> stable
        Flight[] out = new Flight[n];
        for (int i = n - 1; i >= 0; i--) {
            int p = in[i].priority;
            int pos = --count[p];
            out[pos] = in[i];
            if (Trace.on && (n <= 50 || i >= n - 20))
                Trace.log("  place " + in[i].id + " (p" + p + ") from in[" + i + "] -> out[" + pos + "]");
        }
        if (Trace.on && n > 50) Trace.log("  ... (" + (n - 20) + " more placements not shown)");
        return out;
    }

    static String show(int[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(", "); sb.append(i).append(':').append(a[i]); }
        return sb.append(']').toString();
    }
}
