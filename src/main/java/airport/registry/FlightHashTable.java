package airport.registry;

import airport.cli.Trace;
import airport.model.Flight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Own hash table keyed by flight id.
 * Universal hashing h(k) = ((a*k + b) mod p) mod m, p = 2^31 - 1,
 * a in [1,p-1] and b in [0,p-1] drawn from new Random(42).
 * Linear probing, tombstones, rehash when (size+tombstones)/m > 0.5.
 */
public class FlightHashTable {
    public static final long P = 2_147_483_647L;
    private static final byte EMPTY = 0, OCCUPIED = 1, TOMBSTONE = 2;

    private final long a, b;
    private final boolean allowResize;
    private Flight[] slots;
    private byte[] state;
    private int m, size, tombstones, resizes;
    private long totalProbes, operations, collisions;

    public FlightHashTable() { this(16, true); }

    public FlightHashTable(int initialCapacity, boolean allowResize) {
        Random rnd = new Random(42);
        this.a = 1 + Math.floorMod(rnd.nextLong(), P - 1);   // 1 .. p-1
        this.b = Math.floorMod(rnd.nextLong(), P);           // 0 .. p-1
        this.allowResize = allowResize;
        this.m = initialCapacity;
        this.slots = new Flight[m];
        this.state = new byte[m];
    }

    /** For P4's benchmarks: a table with resizing OFF and a fixed capacity so load factor = n/m. */
    public static FlightHashTable buildForBenchmark(int n, double loadFactor) {
        int cap = (int) Math.ceil(n / loadFactor);
        if (cap <= n) cap = n + 1;
        FlightHashTable t = new FlightHashTable(cap, false);
        for (int i = 0; i < n; i++) {
            t.insert(new Flight("BM" + i, "XX", "AAA", "BBB", "R1", i * 5L, i * 5L + 5, 1));
        }
        return t;
    }

    /**
     * Same as buildForBenchmark(n, loadFactor), but with the caller's flights (P4 needs to know
     * exactly which IDs are hits and which are misses). Resizing is OFF, so load factor = n / m.
     */
    public static FlightHashTable buildForBenchmark(Flight[] flights, double loadFactor) {
        if (loadFactor <= 0 || loadFactor >= 1) throw new IllegalArgumentException("load factor must be in (0, 1)");
        int n = flights.length;
        int cap = (int) Math.ceil(n / loadFactor);
        if (cap <= n) cap = n + 1;
        FlightHashTable t = new FlightHashTable(cap, false);
        for (Flight f : flights) t.insert(f);
        return t;
    }

    /** Zero the probe/collision counters, so a benchmark measures only its own lookups. */
    public void resetStats() {
        totalProbes = 0;
        operations = 0;
        collisions = 0;
    }

    private int hash(long k) {
        return (int) (((a * k + b) % P) % m);
    }

    /** Home slot h(encode(id)) in the current table: where probing starts. For TRACE, tests and the interview. */
    public int homeSlot(String id) {
        return hash(IdEncoder.encode(id));
    }

    /** Returns false if the id already exists. */
    public boolean insert(Flight f) {
        int i = hash(IdEncoder.encode(f.id));
        if (state[i] == OCCUPIED) collisions++;
        int firstTomb = -1;
        int probes = 0;
        boolean foundEmpty = false;

        for (int n = 0; n < m; n++) {
            probes++;
            if (state[i] == EMPTY) {
                if (Trace.on) Trace.log("insert probe " + i + " EMPTY");
                foundEmpty = true;
                break;
            }
            if (state[i] == TOMBSTONE) {
                if (Trace.on) Trace.log("insert probe " + i + " TOMBSTONE");
                if (firstTomb < 0) firstTomb = i;
            } else if (slots[i].id.equals(f.id)) {
                if (Trace.on) Trace.log("insert probe " + i + " " + f.id + " DUPLICATE");
                totalProbes += probes;
                operations++;
                return false;
            } else if (Trace.on) {
                Trace.log("insert probe " + i + " " + slots[i].id + " occupied");
            }
            i = (i + 1) % m;
        }

        int target;
        if (firstTomb >= 0) {
            target = firstTomb;
            tombstones--;
        } else if (foundEmpty) {
            target = i;
        } else {
            throw new IllegalStateException("hash table is full");
        }
        slots[target] = f;
        state[target] = OCCUPIED;
        size++;
        totalProbes += probes;
        operations++;
        if (Trace.on) Trace.log("inserted " + f.id + " at slot " + target);

        if (allowResize && (double) (size + tombstones) / m > 0.5) resize();
        return true;
    }

    /** Returns the flight, or null if missing. */
    public Flight get(String id) {
        int i = find(id);
        return i < 0 ? null : slots[i];
    }

    /** Marks the slot as a tombstone. Returns false if the id is missing. */
    public boolean remove(String id) {
        int i = find(id);
        if (i < 0) return false;
        slots[i] = null;
        state[i] = TOMBSTONE;
        size--;
        tombstones++;
        return true;
    }

    private int find(String id) {
        int i = hash(IdEncoder.encode(id));
        int probes = 0;
        int result = -1;
        for (int n = 0; n < m; n++) {
            probes++;
            if (state[i] == EMPTY) {
                if (Trace.on) Trace.log("probe " + i + " EMPTY -> not found");
                break;
            }
            if (state[i] == TOMBSTONE) {
                if (Trace.on) Trace.log("probe " + i + " TOMBSTONE -> skip");
            } else if (slots[i].id.equals(id)) {
                if (Trace.on) Trace.log("probe " + i + " " + id + " MATCH");
                result = i;
                break;
            } else if (Trace.on) {
                Trace.log("probe " + i + " " + slots[i].id + " no match");
            }
            i = (i + 1) % m;
        }
        totalProbes += probes;
        operations++;
        return result;
    }

    private void resize() {
        Flight[] oldSlots = slots;
        byte[] oldState = state;
        int newM = nextPrime(2 * m);
        if (Trace.on) Trace.log("RESIZE m=" + m + " -> " + newM);
        m = newM;
        slots = new Flight[m];
        state = new byte[m];
        tombstones = 0;
        resizes++;
        for (int j = 0; j < oldSlots.length; j++) {
            if (oldState[j] == OCCUPIED) place(oldSlots[j]);
        }
    }

    /** Used only while rehashing: no stats, no duplicate check. */
    private void place(Flight f) {
        int i = hash(IdEncoder.encode(f.id));
        while (state[i] == OCCUPIED) i = (i + 1) % m;
        slots[i] = f;
        state[i] = OCCUPIED;
    }

    private static int nextPrime(int n) {
        int x = Math.max(n, 2);
        while (!isPrime(x)) x++;
        return x;
    }

    private static boolean isPrime(int x) {
        if (x < 2) return false;
        for (int d = 2; (long) d * d <= x; d++) {
            if (x % d == 0) return false;
        }
        return true;
    }

    /** Human-readable parameters, for README, report and interview. */
    public String params() {
        return "p=" + P + ", a=" + a + ", b=" + b + ", m=" + m;
    }

    public int size() { return size; }
    public int capacity() { return m; }
    public double loadFactor() { return (double) size / m; }

    public HashStats stats() {
        return new HashStats(size, m, tombstones, resizes, totalProbes, operations, collisions);
    }

    /** All OCCUPIED flights, in slot order. */
    public List<Flight> all() {
        List<Flight> out = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (state[i] == OCCUPIED) out.add(slots[i]);
        }
        return out;
    }
}
