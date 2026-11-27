package airport.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class Main {

    // {command, prompt1, prompt2, ...}; prompts that mention "optional" may be left blank
    private static final String[][] MENU = {
        {"LOAD", "CSV path (e.g. data/airport_flights.csv)"},
        {"LIST_RUNWAY", "Runway (e.g. R1)"},
        {"FIND", "Flight ID"},
        {"ADD", "ID", "Airline", "Origin", "Destination", "Runway", "Start (yyyy-MM-dd HH:mm)", "End (yyyy-MM-dd HH:mm)", "Priority (1-3)"},
        {"CANCEL", "Flight ID"},
        {"RESCHEDULE", "Flight ID", "New start (yyyy-MM-dd HH:mm)"},
        {"CHECK_CONFLICT", "Runway", "Start (yyyy-MM-dd HH:mm)", "End (yyyy-MM-dd HH:mm)"},
        {"RUNWAY_CLOSURE", "Runway", "From (yyyy-MM-dd HH:mm)", "To (yyyy-MM-dd HH:mm)"},
        {"EMERGENCY_INSERT", "ID", "Airline", "Origin", "Destination", "Runway", "Start (yyyy-MM-dd HH:mm)", "End (yyyy-MM-dd HH:mm)", "Priority (1-3)"},
        {"MASS_CANCEL", "From (yyyy-MM-dd HH:mm)", "To (yyyy-MM-dd HH:mm)"},
        {"SORT_PRIORITY", "Limit (optional)"},
        {"SORT_ID", "Limit (optional)"},
        {"ANALYZE"},
        {"BENCHMARK", "Which (hash|bst|sort|matrix|all)"},
        {"STATS"},
        {"TRACE", "ON or OFF"},
    };

    public static void main(String[] args) throws IOException {
        if (args.length >= 1 && args[0].equals("--batch")) {
            if (args.length < 2) { System.out.println("ERROR: usage: --batch <script file>"); return; }
            batch(args[1]);
        } else {
            menu();
        }
    }

    private static void batch(String file) throws IOException {
        List<String> lines;
        try { lines = Files.readAllLines(Paths.get(file)); }
        catch (IOException e) { System.out.println("ERROR: cannot read script " + file); return; }
        CommandProcessor p = new CommandProcessor(System.out, null);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            System.out.println("> " + line);
            p.execute(line);
            if (!p.isRunning()) break;
        }
    }

    private static void menu() {
        Scanner in = new Scanner(System.in);
        CommandProcessor p = new CommandProcessor(System.out, in);
        while (p.isRunning()) {
            System.out.println();
            System.out.println("=== Airport Chaos ===");
            for (int i = 0; i < MENU.length; i++) System.out.println((i + 1) + " = " + MENU[i][0]);
            System.out.println("0 = EXIT");
            System.out.print("Choose: ");
            if (!in.hasNextLine()) break;
            String choice = in.nextLine().trim();
            if (choice.equals("0")) break;
            int n;
            try { n = Integer.parseInt(choice); } catch (NumberFormatException e) { n = -1; }
            if (n < 1 || n > MENU.length) { System.out.println("ERROR: choose a number from the menu"); continue; }

            String[] item = MENU[n - 1];
            StringBuilder cmd = new StringBuilder(item[0]);
            for (int i = 1; i < item.length; i++) {
                System.out.print(item[i] + ": ");
                String v = in.hasNextLine() ? in.nextLine().trim() : "";
                if (v.isEmpty() && item[i].contains("optional")) continue;
                cmd.append(' ').append(v.contains(" ") || v.isEmpty() ? "\"" + v + "\"" : v);
            }
            p.execute(cmd.toString());
        }
        System.out.println("Bye.");
    }
}
