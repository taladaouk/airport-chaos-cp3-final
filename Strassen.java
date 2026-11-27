package airport.matrix;

import airport.cli.Trace;

/**
 * Part F — recursive Strassen multiplication with 7 sub-products.
 *
 *  1. Pad A and B with zeros up to the next power of two (so every split is exact).
 *  2. If size <= cutoff, use classical multiplication (the recursion "base case").
 *  3. Split into quarters, compute the 7 products M1..M7, combine into C11..C22.
 *  4. Crop the result back to the original N x N.
 *
 * Recurrence: T(n) = 7 T(n/2) + Theta(n^2)   (the n^2 = the 18 quarter-size additions)
 * Master theorem: a = 7, b = 2, n^(log2 7) = n^2.807 grows faster than n^2 -> case 1
 *               -> T(n) = Theta(n^log2(7)) ~ Theta(n^2.807).
 *
 * DEFAULT_CUTOFF = 64 (documented in README + report). Below that, the extra additions
 * and the allocation of many small arrays cost more than the one multiplication we save.
 */
public final class Strassen {
    private Strassen() {}

    public static final int DEFAULT_CUTOFF = 64;

    public static long[][] multiply(long[][] A, long[][] B) { return multiply(A, B, DEFAULT_CUTOFF); }

    public static long[][] multiply(long[][] A, long[][] B, int cutoff) {
        if (cutoff < 1) throw new IllegalArgumentException("cutoff must be >= 1");
        int n = A.length;
        if (B.length != n) throw new IllegalArgumentException("Strassen needs two N x N matrices of the same N");
        for (int i = 0; i < n; i++)
            if (A[i].length != n || B[i].length != n) throw new IllegalArgumentException("matrices must be square");
        if (n == 0) return new long[0][0];

        int m = nextPowerOfTwo(n);
        if (Trace.on) Trace.log("Strassen: N=" + n + " padded to " + m + ", cutoff=" + cutoff);
        long[][] Ap = pad(A, m), Bp = pad(B, m);
        long[][] Cp = strassen(Ap, Bp, cutoff);
        return crop(Cp, n);
    }

    private static long[][] strassen(long[][] A, long[][] B, int cutoff) {
        int n = A.length;
        if (n <= cutoff) return Matrix.multiplyClassical(A, B);

        int h = n / 2;
        long[][] A11 = quarter(A, 0, 0, h), A12 = quarter(A, 0, h, h),
                 A21 = quarter(A, h, 0, h), A22 = quarter(A, h, h, h);
        long[][] B11 = quarter(B, 0, 0, h), B12 = quarter(B, 0, h, h),
                 B21 = quarter(B, h, 0, h), B22 = quarter(B, h, h, h);

        // 10 additions/subtractions to build the operands ...
        long[][] M1 = strassen(Matrix.add(A11, A22), Matrix.add(B11, B22), cutoff);
        long[][] M2 = strassen(Matrix.add(A21, A22), B11, cutoff);
        long[][] M3 = strassen(A11, Matrix.sub(B12, B22), cutoff);
        long[][] M4 = strassen(A22, Matrix.sub(B21, B11), cutoff);
        long[][] M5 = strassen(Matrix.add(A11, A12), B22, cutoff);
        long[][] M6 = strassen(Matrix.sub(A21, A11), Matrix.add(B11, B12), cutoff);
        long[][] M7 = strassen(Matrix.sub(A12, A22), Matrix.add(B21, B22), cutoff);

        // ... and 8 more to combine them: 18 in total per level.
        long[][] C = new long[n][n];
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < h; j++) {
                C[i][j]         = M1[i][j] + M4[i][j] - M5[i][j] + M7[i][j];   // C11
                C[i][j + h]     = M3[i][j] + M5[i][j];                         // C12
                C[i + h][j]     = M2[i][j] + M4[i][j];                         // C21
                C[i + h][j + h] = M1[i][j] - M2[i][j] + M3[i][j] + M6[i][j];   // C22
            }
        }
        return C;
    }

    static int nextPowerOfTwo(int n) {
        int m = 1;
        while (m < n) m <<= 1;
        return m;
    }

    static long[][] pad(long[][] A, int m) {
        int n = A.length;
        if (n == m) return A;
        long[][] P = new long[m][m];                 // new long[][] is already all zeros
        for (int i = 0; i < n; i++) System.arraycopy(A[i], 0, P[i], 0, n);
        return P;
    }

    static long[][] crop(long[][] C, int n) {
        if (C.length == n) return C;
        long[][] R = new long[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(C[i], 0, R[i], 0, n);
        return R;
    }

    private static long[][] quarter(long[][] M, int r0, int c0, int h) {
        long[][] Q = new long[h][h];
        for (int i = 0; i < h; i++) System.arraycopy(M[r0 + i], c0, Q[i], 0, h);
        return Q;
    }
}
