package airport.bench;

import airport.bst.RunwayBST;
import airport.matrix.Matrix;
import airport.matrix.Strassen;
import airport.model.Flight;
import airport.registry.FlightHashTable;
import airport.sort.CountingSort;
import airport.sort.MergeSort;
import airport.sort.RadixSort;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

/**
 * Benchmark harness (P4).
 *
 * Method for every case: 3 untimed warm-up runs (so the JIT compiler has compiled the hot
 * code), then 5 timed runs with System.nanoTime(); we report the MEDIAN (robust to one slow
 * run caused by GC or the OS). All synthetic data uses new Random(2026).
 *
 * Usage:  java -cp target/classes airport.bench.BenchmarkRunner <hash|bst|sort|matrix|all> [--quick] [--out results]
 *         --quick uses smaller sizes (for a fast demo in the interview).
 * The CLI command "BENCHMARK <which>" (P3) should call BenchmarkRunner.run(which, quick, outDir).
 */
public final class BenchmarkRunner {
    static final int WARMUP = 3, REPEATS = 5;
    static volatile Object sink;   // stops the JIT from deleting "useless" work

    public static void main(String[] args) throws IOException {
        String which = args.length > 0 ? args[0].toLowerCase() : "all";
        boolean quick = false;
        String out = "results";
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--quick")) quick = true;
            else if (args[i].equals("--out") && i + 1 < args.length) out = args[++i];
        }
        run(which, quick, Paths.get(out));
    }

    public static void run(String which, boolean quick, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        writeEnvironment(outDir.resolve("environment.txt"));
        boolean all = which.equals("all");
        if (all || which.equals("hash"))   hash(quick, outDir);
        if (all || which.equals("bst"))    bst(quick, outDir);
        if (all || which.equals("sort"))   sort(quick, outDir);
        if (all || which.equals("matrix")) matrix(quick, outDir);
        if (!all && !which.matches("hash|bst|sort|matrix"))
            System.out.println("ERROR: BENCHMARK expects hash|bst|sort|matrix|all");
    }

    // ------------------------------------------------------------------ timing helper
    /** Median of REPEATS timed runs (ns) after WARMUP untimed runs. */
    static long medianNs(Runnable r) {
        for (int i = 0; i < WARMUP; i++) r.run();
        long[] t = new long[REPEATS];
        for (int i = 0; i < REPEATS; i++) {
            long s = System.nanoTime();
            r.run();
            t[i] = System.nanoTime() - s;
        }
        // insertion sort on 5 numbers — no library sort needed here either
        for (int i = 1; i < t.length; i++) {
            long x = t[i]; int j = i - 1;
            while (j >= 0 && t[j] > x) { t[j + 1] = t[j]; j--; }
            t[j + 1] = x;
        }
        return t[REPEATS / 2];
    }

    // ------------------------------------------------------------------ 1. hash lookups
    static void hash(boolean quick, Path dir) throws IOException {
        int n = quick ? 20_000 : 100_000;
        double[] alphas = { 0.25, 0.5, 0.75, 0.9 };
        Object[] hm = SyntheticData.hitsAndMisses(n, new Random(SyntheticData.SEED));
        Flight[] hits = (Flight[]) hm[0];
        String[] misses = (String[]) hm[1];

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("bench_hash.csv")))) {
            w.println("n,load_factor,actual_load,avg_probes_hit,avg_probes_miss,theory_hit,theory_miss,ns_per_hit,ns_per_miss");
            System.out.println("\n== Hash lookup, n = " + n + " (linear probing, fixed capacity) ==");
            System.out.printf("%-6s %-8s %-10s %-10s %-10s %-10s %-9s %-9s%n",
                    "alpha", "actual", "probe hit", "probe miss", "theory hit", "theory miss", "ns/hit", "ns/miss");
            for (double a : alphas) {
                FlightHashTable t = FlightHashTable.buildForBenchmark(hits, a);
                double actual = t.stats().loadFactor;

                long p0 = t.stats().totalProbes;
                for (Flight f : hits) sink = t.get(f.id);
                double probeHit = (double) (t.stats().totalProbes - p0) / n;
                p0 = t.stats().totalProbes;
                for (String id : misses) sink = t.get(id);
                double probeMiss = (double) (t.stats().totalProbes - p0) / n;

                long nsHit = medianNs(() -> { for (Flight f : hits) sink = t.get(f.id); });
                long nsMiss = medianNs(() -> { for (String id : misses) sink = t.get(id); });

                // Knuth's estimates for linear probing
                double thHit = 0.5 * (1 + 1 / (1 - actual));
                double thMiss = 0.5 * (1 + 1 / ((1 - actual) * (1 - actual)));
                w.printf("%d,%.2f,%.4f,%.3f,%.3f,%.3f,%.3f,%.1f,%.1f%n", n, a, actual, probeHit, probeMiss,
                        thHit, thMiss, (double) nsHit / n, (double) nsMiss / n);
                System.out.printf("%-6.2f %-8.4f %-10.3f %-10.3f %-10.3f %-11.3f %-9.1f %-9.1f%n", a, actual,
                        probeHit, probeMiss, thHit, thMiss, (double) nsHit / n, (double) nsMiss / n);
            }
        }
    }

    // ------------------------------------------------------------------ 2. BST sorted vs shuffled
    static void bst(boolean quick, Path dir) throws IOException {
        int[] sizes = quick ? new int[] { 1_000, 2_000, 5_000 } : new int[] { 1_000, 5_000, 10_000, 20_000 };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("bench_bst.csv")))) {
            w.println("n,order,height,insert_ms,search_all_ms");
            System.out.println("\n== BST insert + search (unbalanced) ==");
            System.out.printf("%-7s %-9s %-7s %-10s %-10s%n", "n", "order", "height", "insert ms", "search ms");
            for (int n : sizes) {
                long[] sorted = SyntheticData.sortedKeys(n);
                long[] shuffled = SyntheticData.shuffled(sorted, new Random(SyntheticData.SEED));
                for (int o = 0; o < 2; o++) {
                    long[] keys = o == 0 ? sorted : shuffled;
                    String name = o == 0 ? "sorted" : "shuffled";
                    long ins = medianNs(() -> sink = RunwayBST.buildFromKeys(keys));
                    RunwayBST t = RunwayBST.buildFromKeys(keys);
                    // buildFromKeys names the flight for keys[i] "B" + i, so (keys[i], ids[i]) is a key
                    // that IS in the tree: every search below is a successful search (a hit).
                    // The ids are built before timing so string concatenation is not measured.
                    String[] ids = new String[n];
                    for (int i = 0; i < n; i++) ids[i] = "B" + i;
                    long srch = medianNs(() -> {
                        for (int i = 0; i < keys.length; i++) {
                            Object hit = t.search(keys[i], ids[i]);
                            if (hit == null) throw new IllegalStateException("BST benchmark: key " + ids[i] + " not found");
                            sink = hit;
                        }
                    });
                    int h = t.height();
                    w.printf("%d,%s,%d,%.3f,%.3f%n", n, name, h, ins / 1e6, srch / 1e6);
                    System.out.printf("%-7d %-9s %-7d %-10.3f %-10.3f%n", n, name, h, ins / 1e6, srch / 1e6);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 3. sorting
    static void sort(boolean quick, Path dir) throws IOException {
        int[] sizes = quick ? new int[] { 10_000, 100_000 } : new int[] { 10_000, 100_000, 1_000_000 };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("bench_sort.csv")))) {
            w.println("n,algorithm,key,ms,ns_per_element");
            System.out.println("\n== Sorting ==");
            System.out.printf("%-9s %-40s %-10s%n", "n", "algorithm", "ms");
            for (int n : sizes) {
                Flight[] data = SyntheticData.flights(n, new Random(SyntheticData.SEED));
                row(w, n, "counting sort", "priority", medianNs(() -> sink = CountingSort.byPriority(data)));
                row(w, n, "merge sort (ours)", "priority", medianNs(() -> sink = MergeSort.byPriority(data)));
                row(w, n, "Arrays.sort (library baseline)", "priority", medianNs(() -> {
                    Flight[] c = data.clone(); Arrays.sort(c, MergeSort.BY_PRIORITY); sink = c; }));
                row(w, n, "LSD radix sort", "flight_id", medianNs(() -> sink = RadixSort.byId(data)));
                row(w, n, "merge sort (ours)", "flight_id", medianNs(() -> sink = MergeSort.byId(data)));
                row(w, n, "Arrays.sort (library baseline)", "flight_id", medianNs(() -> {
                    Flight[] c = data.clone(); Arrays.sort(c, MergeSort.BY_ID); sink = c; }));
                row(w, n, "LSD radix sort", "originalStart", medianNs(() -> sink = RadixSort.stableSortByStart(data)));
            }
        }
    }

    private static void row(PrintWriter w, int n, String alg, String key, long ns) {
        w.printf("%d,%s,%s,%.3f,%.2f%n", n, alg, key, ns / 1e6, (double) ns / n);
        System.out.printf("%-9d %-40s %-10.3f%n", n, alg + " [" + key + "]", ns / 1e6);
    }

    // ------------------------------------------------------------------ 4. matrices
    static void matrix(boolean quick, Path dir) throws IOException {
        int[] sizes = quick ? new int[] { 16, 32, 64, 128 } : new int[] { 16, 32, 64, 128, 256, 512 };
        int[] cutoffs = { 1, 32, 64 };
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("bench_matrix.csv")))) {
            w.println("N,algorithm,cutoff,ms,correct");
            System.out.println("\n== Matrix multiplication (entries 0..9) ==");
            System.out.printf("%-5s %-22s %-10s %-7s%n", "N", "algorithm", "ms", "equal?");
            // Global JIT warm-up: without it the FIRST (smallest) case is timed while the code is
            // still interpreted, and N=16 looked slower than N=32.
            Random wr = new Random(1);
            long[][] W = Matrix.random(64, 9, wr);
            for (int i = 0; i < 30; i++) { sink = Matrix.multiplyClassical(W, W); sink = Strassen.multiply(W, W, 8); }
            for (int N : sizes) {
                Random rnd = new Random(SyntheticData.SEED);
                long[][] A = Matrix.random(N, 9, rnd), B = Matrix.random(N, 9, rnd);
                long[][] ref = Matrix.multiplyClassical(A, B);
                long tc = medianNs(() -> sink = Matrix.multiplyClassical(A, B));
                w.printf("%d,classical,,%.3f,true%n", N, tc / 1e6);
                System.out.printf("%-5d %-22s %-10.3f %-7s%n", N, "classical", tc / 1e6, "ref");
                for (int c : cutoffs) {
                    boolean ok = Matrix.equal(ref, Strassen.multiply(A, B, c));
                    long ts = medianNs(() -> sink = Strassen.multiply(A, B, c));
                    w.printf("%d,strassen,%d,%.3f,%b%n", N, c, ts / 1e6, ok);
                    System.out.printf("%-5d %-22s %-10.3f %-7s%n", N, "strassen cutoff=" + c, ts / 1e6, ok);
                }
            }
        }
    }

    // ------------------------------------------------------------------ environment
    static void writeEnvironment(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("os           : ").append(System.getProperty("os.name")).append(' ')
          .append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
        sb.append("cpu          : ").append(cpuModel()).append('\n');
        sb.append("logical cpus : ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        sb.append("max heap MB  : ").append(Runtime.getRuntime().maxMemory() / (1024 * 1024)).append('\n');
        sb.append("java         : ").append(System.getProperty("java.version")).append(" (")
          .append(System.getProperty("java.vm.name")).append(")\n");
        sb.append("jvm flags    : ").append(ManagementFactory.getRuntimeMXBean().getInputArguments()).append('\n');
        sb.append("method       : ").append(WARMUP).append(" warm-up + ").append(REPEATS)
          .append(" timed runs, median, System.nanoTime(), seed 2026\n");
        Files.writeString(file, sb.toString());
    }

    private static String cpuModel() {
        try {
            Path p = Paths.get("/proc/cpuinfo");
            if (Files.exists(p))
                for (String line : Files.readAllLines(p))
                    if (line.startsWith("model name")) return line.substring(line.indexOf(':') + 1).trim();
        } catch (IOException ignored) { }
        String env = System.getenv("PROCESSOR_IDENTIFIER");       // Windows
        return env != null ? env : "unknown (record it by hand: Windows Task Manager / macOS About This Mac)";
    }
}
