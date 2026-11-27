package airport.io;

import airport.model.Flight;
import airport.model.TimeUtil;
import airport.registry.FlightHashTable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class CsvLoader {
    private CsvLoader() {}

    /** One-argument version: uses a temporary registry for duplicate checking. */
    public static LoadResult load(String path) {
        return load(path, new FlightHashTable());
    }

    /** Loads the CSV into the given registry. Bad rows are skipped and reported. */
    public static LoadResult load(String path, FlightHashTable registry) {
        LoadResult result = new LoadResult();
        Path p = Paths.get(path);
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line = br.readLine();            // header
            if (line == null) {
                result.errors.add("ERROR: file is empty");
                return result;
            }
            int lineNo = 1;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) continue;
                parseLine(line, lineNo, registry, result);
            }
        } catch (IOException e) {
            result.errors.add("ERROR: cannot read file " + path + " (" + e.getMessage() + ")");
        }
        return result;
    }

    /** Same, but from lines already in memory (used by tests). First line is the header. */
    public static LoadResult loadLines(List<String> lines, FlightHashTable registry) {
        LoadResult result = new LoadResult();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) continue;
            parseLine(lines.get(i), i + 1, registry, result);
        }
        return result;
    }

    private static List<String> split(String line) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ',') {
                parts.add(line.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(line.substring(start).trim());
        return parts;
    }

    private static void parseLine(String line, int lineNo, FlightHashTable registry, LoadResult r) {
        List<String> f = split(line);
        if (f.size() != 9) {
            r.errors.add("ERROR line " + lineNo + ": expected 9 fields, got " + f.size());
            return;
        }
        String id = f.get(0), airline = f.get(1), origin = f.get(2), dest = f.get(3);
        String runway = f.get(4);

        if (id.isEmpty()) {
            r.errors.add("ERROR line " + lineNo + ": empty flight_id");
            return;
        }
        if (!isRunway(runway)) {
            r.errors.add("ERROR line " + lineNo + ": invalid runway '" + runway + "'");
            return;
        }
        long start, end;
        try {
            start = TimeUtil.parse(f.get(5));
        } catch (IllegalArgumentException e) {
            r.errors.add("ERROR line " + lineNo + ": bad start_time '" + f.get(5) + "'");
            return;
        }
        try {
            end = TimeUtil.parse(f.get(6));
        } catch (IllegalArgumentException e) {
            r.errors.add("ERROR line " + lineNo + ": bad end_time '" + f.get(6) + "'");
            return;
        }
        if (start >= end) {
            r.errors.add("ERROR line " + lineNo + ": start_time must be before end_time");
            return;
        }
        int priority;
        try {
            priority = Integer.parseInt(f.get(7));
        } catch (NumberFormatException e) {
            r.errors.add("ERROR line " + lineNo + ": priority is not a number '" + f.get(7) + "'");
            return;
        }
        if (priority < 1 || priority > 3) {
            r.errors.add("ERROR line " + lineNo + ": priority must be 1, 2 or 3, got " + priority);
            return;
        }
        if (!f.get(8).equals("SCHEDULED")) {
            r.errors.add("ERROR line " + lineNo + ": status must be SCHEDULED, got '" + f.get(8) + "'");
            return;
        }

        Flight flight = new Flight(id, airline, origin, dest, runway, start, end, priority);
        if (!registry.insert(flight)) {
            r.errors.add("ERROR line " + lineNo + ": duplicate flight_id " + id);
            return;
        }
        r.loaded++;
        r.flights.add(flight);
    }

    /** Runway must look like R followed by digits (not hardcoded to R1-R5). */
    private static boolean isRunway(String s) {
        if (s.length() < 2 || s.charAt(0) != 'R') return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
