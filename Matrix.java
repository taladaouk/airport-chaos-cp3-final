package airport.matrix;

import java.util.Random;

/** Part F — matrix helpers and the classical O(n^3) multiplication. Entries are long. */
public final class Matrix {
    private Matrix() {}

    /**
     * Classical multiplication C = A x B, in i-k-j loop order.
     *
     * Why i-k-j and not i-j-k: Java stores a 2D array row by row. In i-k-j the inner loop
     * walks along row k of B and row i of C — both contiguous in memory — so the CPU cache
     * is used well. In i-j-k the inner loop walks DOWN a column of B, jumping to a new row
     * every step (cache misses). Same n^3 multiplications, same result, faster in practice.
     */
    public static long[][] multiplyClassical(long[][] A, long[][] B) {
        int n = A.length, m = B.length, p = (m == 0) ? 0 : B[0].length;
        if (n > 0 && A[0].length != m)
            throw new IllegalArgumentException("dimension mismatch: " + n + "x" + A[0].length + " times " + m + "x" + p);
        long[][] C = new long[n][p];
        for (int i = 0; i < n; i++) {
            long[] Ci = C[i], Ai = A[i];
            for (int k = 0; k < m; k++) {
                long aik = Ai[k];
                if (aik == 0) continue;              // route matrices are sparse: skip zero rows of work
                long[] Bk = B[k];
                for (int j = 0; j < p; j++) Ci[j] += aik * Bk[j];
            }
        }
        return C;
    }

    public static long[][] add(long[][] A, long[][] B) {
        int n = A.length;
        long[][] C = new long[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) C[i][j] = A[i][j] + B[i][j];
        return C;
    }

    public static long[][] sub(long[][] A, long[][] B) {
        int n = A.length;
        long[][] C = new long[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) C[i][j] = A[i][j] - B[i][j];
        return C;
    }

    public static boolean equal(long[][] A, long[][] B) {
        if (A.length != B.length) return false;
        for (int i = 0; i < A.length; i++) {
            if (A[i].length != B[i].length) return false;
            for (int j = 0; j < A[i].length; j++) if (A[i][j] != B[i][j]) return false;
        }
        return true;
    }

    /** n x n matrix with entries 0..maxVal (inclusive). */
    public static long[][] random(int n, int maxVal, Random rnd) {
        long[][] M = new long[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) M[i][j] = rnd.nextInt(maxVal + 1);
        return M;
    }
}
