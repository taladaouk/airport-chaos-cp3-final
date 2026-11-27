package airport.analysis;

/**
 * Maximum subarray: divide and conquer O(n log n) and brute force O(n^2).
 * Tie rule (both versions): bigger sum, then SHORTER range, then earlier start.
 * Why shorter before earlier: disruption loads are never negative, so with "earlier start first"
 * the winning range would stretch back over empty buckets to midnight (e.g. 00:00-11:00).
 * "Shorter first" trims those zero buckets and reports the real disrupted window.
 * All zeros -> [0,0], sum 0 (every single cell ties at length 1; the earliest is cell 0).
 */
public class MaxSubarray {

    public static class Result {
        public final int lo, hi;
        public final long sum;
        public Result(int lo, int hi, long sum) { this.lo = lo; this.hi = hi; this.sum = sum; }
        public boolean sameAs(Result o) { return lo == o.lo && hi == o.hi && sum == o.sum; }
        @Override public String toString() { return "[" + lo + ", " + hi + "] sum=" + sum; }
    }

    /** true if a is strictly better than b under the tie rule. */
    static boolean better(Result a, Result b) {
        if (b == null) return true;
        if (a.sum != b.sum) return a.sum > b.sum;
        int lenA = a.hi - a.lo, lenB = b.hi - b.lo;
        if (lenA != lenB) return lenA < lenB;
        return a.lo < b.lo;
    }

    public static Result divideAndConquer(long[] a) {
        if (a.length == 0) return new Result(0, 0, 0);
        return best(a, 0, a.length - 1);
    }

    private static Result best(long[] a, int lo, int hi) {
        if (lo == hi) return new Result(lo, lo, a[lo]);
        int mid = (lo + hi) / 2;
        Result left = best(a, lo, mid);
        Result right = best(a, mid + 1, hi);

        // best range crossing mid: scan left from mid, scan right from mid+1
        long sum = 0, bestL = Long.MIN_VALUE;
        int l = mid;
        for (int i = mid; i >= lo; i--) {
            sum += a[i];
            if (sum > bestL) { bestL = sum; l = i; }    // tie -> keep the one nearer mid (shorter range)
        }
        sum = 0;
        long bestR = Long.MIN_VALUE;
        int r = mid + 1;
        for (int j = mid + 1; j <= hi; j++) {
            sum += a[j];
            if (sum > bestR) { bestR = sum; r = j; }    // tie -> shorter range
        }
        Result cross = new Result(l, r, bestL + bestR);

        Result res = left;
        if (better(right, res)) res = right;
        if (better(cross, res)) res = cross;
        return res;
    }

    public static Result brute(long[] a) {
        if (a.length == 0) return new Result(0, 0, 0);
        Result best = null;
        for (int i = 0; i < a.length; i++) {
            long sum = 0;
            for (int j = i; j < a.length; j++) {
                sum += a[j];
                Result c = new Result(i, j, sum);
                if (better(c, best)) best = c;
            }
        }
        return best;
    }
}
