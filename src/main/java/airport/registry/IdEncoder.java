package airport.registry;

public final class IdEncoder {
    public static final long P1 = 1_000_000_007L;

    private IdEncoder() {}

    /** Polynomial encoding: k = (k*131 + c) mod P1. Result is 0 <= k < P1. */
    public static long encode(String id) {
        long k = 0;
        for (int i = 0; i < id.length(); i++) {
            k = (k * 131 + id.charAt(i)) % P1;
        }
        return k;
    }
}
