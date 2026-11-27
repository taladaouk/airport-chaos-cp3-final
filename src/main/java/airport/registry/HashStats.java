package airport.registry;

public final class HashStats {
    public final int size, capacity, tombstones, resizes;
    public final double loadFactor, averageProbes;
    public final long totalProbes, operations, collisions;

    public HashStats(int size, int capacity, int tombstones, int resizes,
                     long totalProbes, long operations, long collisions) {
        this.size = size;
        this.capacity = capacity;
        this.tombstones = tombstones;
        this.resizes = resizes;
        this.totalProbes = totalProbes;
        this.operations = operations;
        this.collisions = collisions;
        this.loadFactor = capacity == 0 ? 0 : (double) size / capacity;
        this.averageProbes = operations == 0 ? 0 : (double) totalProbes / operations;
    }

    @Override
    public String toString() {
        return String.format(
            "Hash table: size=%d, m=%d, load factor=%.3f, tombstones=%d, "
          + "total probes=%d, avg probes=%.3f, collisions=%d, resizes=%d",
            size, capacity, loadFactor, tombstones, totalProbes, averageProbes, collisions, resizes);
    }
}
