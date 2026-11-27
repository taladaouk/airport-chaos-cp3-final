package airport.sort;

import airport.model.Flight;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Our own top-down merge sort: the COMPARISON-SORT baseline for the benchmarks.
 * O(n log n) time in every case, O(n) extra space, stable (ties take the LEFT element).
 * It also counts comparisons so the benchmark can show ~ n log2 n comparisons.
 */
public final class MergeSort {
    private MergeSort() {}

    public static long lastComparisons;

    public static final Comparator<Flight> BY_ID = (x, y) -> x.id.compareTo(y.id);
    public static final Comparator<Flight> BY_PRIORITY = (x, y) -> Integer.compare(x.priority, y.priority);

    public static Flight[] byId(Flight[] a) { return sort(a, BY_ID); }
    public static Flight[] byPriority(Flight[] a) { return sort(a, BY_PRIORITY); }

    /** Returns a NEW sorted array. */
    public static <T> T[] sort(T[] input, Comparator<? super T> cmp) {
        lastComparisons = 0;
        T[] a = Arrays.copyOf(input, input.length);
        T[] tmp = Arrays.copyOf(input, input.length);
        mergeSort(a, tmp, 0, a.length - 1, cmp);
        return a;
    }

    private static <T> void mergeSort(T[] a, T[] tmp, int lo, int hi, Comparator<? super T> cmp) {
        if (lo >= hi) return;
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, cmp);
        mergeSort(a, tmp, mid + 1, hi, cmp);
        merge(a, tmp, lo, mid, hi, cmp);
    }

    private static <T> void merge(T[] a, T[] tmp, int lo, int mid, int hi, Comparator<? super T> cmp) {
        for (int k = lo; k <= hi; k++) tmp[k] = a[k];
        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) a[k] = tmp[j++];
            else if (j > hi) a[k] = tmp[i++];
            else {
                lastComparisons++;
                // "<= 0" takes the left element on a tie -> stable
                a[k] = cmp.compare(tmp[i], tmp[j]) <= 0 ? tmp[i++] : tmp[j++];
            }
        }
    }
}
