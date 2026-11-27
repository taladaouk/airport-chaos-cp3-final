package airport.bench;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws one PNG line chart per benchmark from results/bench_*.csv (plain Java, no libraries).
 *
 * Usage:  java -cp target/classes airport.bench.PlotBenchmarks [resultsDir]
 * Output: plot_hash.png, plot_bst.png, plot_sort.png, plot_matrix.png in the same folder.
 */
public final class PlotBenchmarks {
    private PlotBenchmarks() {}

    // fixed colour order + a different marker per series, so the charts also read in black and white
    static final Color[] COLORS = { new Color(0x257de0), new Color(0xf47230), new Color(0x00b894),
            new Color(0xf2a600), new Color(0xee7fac), new Color(0x008a17), new Color(0x7b61ff) };
    static final int W = 900, H = 560, LEFT = 90, RIGHT = 30, TOP = 60, BOTTOM = 70;

    /** One line on a chart: a name and its (x, y) points. */
    static final class Series {
        final String name; final List<double[]> pts = new ArrayList<>();
        Series(String name) { this.name = name; }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "results");

        // 1) hash: average probes vs load factor, measured and theory
        List<String[]> rows = readCsv(dir.resolve("bench_hash.csv"));
        Series mm = new Series("miss (measured)"), mt = new Series("miss (theory)"),
               hm = new Series("hit (measured)"), ht = new Series("hit (theory)");
        for (String[] r : rows) {
            double a = d(r[1]);
            mm.pts.add(new double[] { a, d(r[4]) }); mt.pts.add(new double[] { a, d(r[6]) });
            hm.pts.add(new double[] { a, d(r[3]) }); ht.pts.add(new double[] { a, d(r[5]) });
        }
        draw(dir.resolve("plot_hash.png"), "Linear probing: average probes per lookup", "load factor",
             "probes", List.of(mm, mt, hm, ht), false, false);

        // 2) BST: build + search time, sorted vs shuffled
        rows = readCsv(dir.resolve("bench_bst.csv"));
        Series sorted = new Series("sorted keys"), shuffled = new Series("shuffled keys");
        for (String[] r : rows) {
            Series s = r[1].equals("sorted") ? sorted : shuffled;
            s.pts.add(new double[] { d(r[0]), d(r[3]) + d(r[4]) });
        }
        draw(dir.resolve("plot_bst.png"), "Unbalanced BST: build + search all n keys", "n",
             "time (ms, log)", List.of(sorted, shuffled), false, true);

        // 3) sorting: one line per (algorithm, key)
        rows = readCsv(dir.resolve("bench_sort.csv"));
        List<Series> sorts = new ArrayList<>();
        for (String[] r : rows) {
            String name = r[1] + " [" + r[2] + "]";
            Series s = find(sorts, name);
            s.pts.add(new double[] { d(r[0]), d(r[3]) });
        }
        draw(dir.resolve("plot_sort.png"), "Sorting: counting/radix vs comparison sorts", "n (log)",
             "time (ms, log)", sorts, true, true);

        // 4) matrix: classical and Strassen per cutoff
        rows = readCsv(dir.resolve("bench_matrix.csv"));
        List<Series> mats = new ArrayList<>();
        for (String[] r : rows) {
            String name = r[1].equals("classical") ? "classical" : "Strassen cutoff=" + r[2];
            find(mats, name).pts.add(new double[] { d(r[0]), Math.max(d(r[3]), 1e-3) });
        }
        draw(dir.resolve("plot_matrix.png"), "Matrix multiplication: classical vs Strassen", "N (log)",
             "time (ms, log)", mats, true, true);
    }

    static Series find(List<Series> list, String name) {
        for (Series s : list) if (s.name.equals(name)) return s;
        Series s = new Series(name);
        list.add(s);
        return s;
    }

    static double d(String s) { return Double.parseDouble(s.trim()); }

    /**
     * Reads a CSV file, skipping the header line. Our CSVs have no commas inside values.
     * A missing file (that benchmark was not run) gives an empty list, so the other plots are still drawn.
     */
    static List<String[]> readCsv(Path file) throws IOException {
        List<String[]> out = new ArrayList<>();
        if (!Files.exists(file)) {
            System.out.println("skipped " + file + " (not found - run that benchmark first)");
            return out;
        }
        List<String> lines = Files.readAllLines(file);
        for (int i = 1; i < lines.size(); i++)
            if (!lines.get(i).isBlank()) out.add(lines.get(i).split(",", -1));
        return out;
    }

    /** Maps a data value to a 0..1 position on an axis (log or linear). */
    static double pos(double v, double lo, double hi, boolean log) {
        if (log) return (Math.log10(v) - Math.log10(lo)) / (Math.log10(hi) - Math.log10(lo));
        return (v - lo) / (hi - lo);
    }

    static void draw(Path out, String title, String xLabel, String yLabel, List<Series> series,
                     boolean logX, boolean logY) throws IOException {
        boolean any = false;
        for (Series s : series) if (!s.pts.isEmpty()) any = true;
        if (!any) return;                                   // no data for this chart: nothing to draw
        // data range
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (Series s : series)
            for (double[] p : s.pts) {
                x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]);
                y0 = Math.min(y0, p[1]); y1 = Math.max(y1, p[1]);
            }
        if (logY) { y0 = Math.pow(10, Math.floor(Math.log10(y0))); y1 = Math.pow(10, Math.ceil(Math.log10(y1))); }
        else { y0 = 0; y1 = niceStep(y1 / 5) * 5; }
        if (!logX) { double pad = (x1 - x0) * 0.05; x0 -= pad; x1 += pad; }
        else { x0 /= 1.3; x1 *= 1.3; }

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        int pw = W - LEFT - RIGHT, ph = H - TOP - BOTTOM;

        // title and axis labels
        g.setColor(new Color(0x1f1f1f));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(title, LEFT, 35);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(0x555555));
        g.drawString(xLabel, LEFT + pw / 2 - g.getFontMetrics().stringWidth(xLabel) / 2, H - 20);
        g.rotate(-Math.PI / 2);
        g.drawString(yLabel, -(TOP + ph / 2) - g.getFontMetrics().stringWidth(yLabel) / 2, 25);
        g.rotate(Math.PI / 2);

        // grid + tick labels
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        List<Double> yt = new ArrayList<>();
        if (logY) for (double v = y0; v <= y1 * 1.0001; v *= 10) yt.add(v);
        else for (int i = 0; i <= 5; i++) yt.add(y1 * i / 5);
        for (double v : yt) {
            int py = TOP + ph - (int) Math.round(pos(v, y0, y1, logY) * ph);
            g.setColor(new Color(0xe6e6e6));
            g.drawLine(LEFT, py, LEFT + pw, py);
            g.setColor(new Color(0x555555));
            String lab = fmt(v);
            g.drawString(lab, LEFT - 8 - g.getFontMetrics().stringWidth(lab), py + 4);
        }
        List<Double> xt = new ArrayList<>();
        for (Series s : series) for (double[] p : s.pts) if (!xt.contains(p[0])) xt.add(p[0]);
        for (double v : xt) {
            int px = LEFT + (int) Math.round(pos(v, x0, x1, logX) * pw);
            g.setColor(new Color(0xe6e6e6));
            g.drawLine(px, TOP, px, TOP + ph);
            g.setColor(new Color(0x555555));
            String lab = fmt(v);
            g.drawString(lab, px - g.getFontMetrics().stringWidth(lab) / 2, TOP + ph + 18);
        }
        g.setColor(new Color(0xbdbdbd));
        g.drawLine(LEFT, TOP + ph, LEFT + pw, TOP + ph);
        g.drawLine(LEFT, TOP, LEFT, TOP + ph);

        // series: 2px lines + markers
        for (int si = 0; si < series.size(); si++) {
            Series s = series.get(si);
            g.setColor(COLORS[si % COLORS.length]);
            g.setStroke(new BasicStroke(2f));
            int prevX = -1, prevY = -1;
            for (double[] p : s.pts) {
                int px = LEFT + (int) Math.round(pos(p[0], x0, x1, logX) * pw);
                int py = TOP + ph - (int) Math.round(pos(p[1], y0, y1, logY) * ph);
                if (prevX >= 0) g.drawLine(prevX, prevY, px, py);
                marker(g, si, px, py);
                prevX = px; prevY = py;
            }
        }

        // legend (top-left inside the plot)
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int ly = TOP + 18;
        for (int si = 0; si < series.size(); si++) {
            g.setColor(COLORS[si % COLORS.length]);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(LEFT + 14, ly - 4, LEFT + 40, ly - 4);
            marker(g, si, LEFT + 27, ly - 4);
            g.setColor(new Color(0x1f1f1f));
            g.drawString(series.get(si).name, LEFT + 48, ly);
            ly += 18;
        }
        g.dispose();
        ImageIO.write(img, "png", new File(out.toString()));
        System.out.println("wrote " + out);
    }

    /** Different marker shape per series index: circle, square, triangle, diamond, ... */
    static void marker(Graphics2D g, int si, int x, int y) {
        int r = 5;
        switch (si % 4) {
            case 0 -> g.fillOval(x - r, y - r, 2 * r, 2 * r);
            case 1 -> g.fillRect(x - r, y - r, 2 * r, 2 * r);
            case 2 -> g.fillPolygon(new int[] { x, x - r - 1, x + r + 1 }, new int[] { y - r - 1, y + r, y + r }, 3);
            default -> g.fillPolygon(new int[] { x, x - r - 1, x, x + r + 1 }, new int[] { y - r - 1, y, y + r + 1, y }, 4);
        }
        if (si >= 4) { g.setColor(Color.WHITE); g.fillOval(x - 2, y - 2, 4, 4); g.setColor(COLORS[si % COLORS.length]); }
    }

    /** Rounds a tick step up to 1, 2 or 5 times a power of ten, so axis labels are round numbers. */
    static double niceStep(double raw) {
        double p = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double m : new double[] { 1, 2, 5, 10 }) if (m * p >= raw) return m * p;
        return 10 * p;
    }

    static String fmt(double v) {
        if (v == 0) return "0";
        if (v >= 1000 && v == Math.rint(v)) return String.format("%,d", (long) v);
        if (v >= 100) return String.format("%.0f", v);
        if (v >= 1) return String.format("%.2f", v).replaceAll("\\.?0+$", "");
        return String.format("%.3f", v).replaceAll("0+$", "");
    }
}
