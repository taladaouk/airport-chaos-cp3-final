package airport.regression;

import airport.cli.CommandProcessor;
import airport.model.Flight;
import airport.sort.RadixSort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test 18 — fresh-load regression, one test PER official scenario (owner P4).
 * Handout: "Include a fresh-load regression test for each scenario."
 *
 * For each scenario S we run it twice:
 *   clean : LOAD -> S
 *   dirty : LOAD -> (a DIFFERENT scenario, to mutate state) -> LOAD -> S
 * S's report and the whole registry state must be IDENTICAL in both runs, and the registry and
 * BSTs must be consistent. This proves LOAD resets ALL state (new registry, new BSTs), so nothing
 * from an earlier scenario leaks into the next one.
 *
 * Uses P3's real CommandProcessor(PrintStream out, Scanner in): in = null is batch mode, and we
 * capture what each command prints. registry() / scheduler() give the state; P2's
 * checkConsistency returns List<String> (empty = OK). Command strings = scripts/scenario*.txt.
 */
class FreshLoadRegressionTest {

    static final String CSV = "data/airport_flights.csv";
    static final String A = "RUNWAY_CLOSURE R1 \"2026-11-27 09:00\" \"2026-11-27 11:00\"";
    static final String B = "EMERGENCY_INSERT EM90001 EM JFK BOS R1 \"2026-11-27 09:00\" \"2026-11-27 09:05\" 1 --accept";
    static final String C = "MASS_CANCEL \"2026-11-27 14:00\" \"2026-11-27 15:00\"";

    @Test
    void scenarioAAfterReloadEqualsScenarioAFromCleanStart() {
        assertFreshLoad(A, C);                  // mutate with C (cancellations), reload, then A
    }

    @Test
    void scenarioBAfterReloadEqualsScenarioBFromCleanStart() {
        assertFreshLoad(B, A);                  // mutate with A (R1 delays), reload, then B on R1
    }

    @Test
    void scenarioCAfterReloadEqualsScenarioCFromCleanStart() {
        assertFreshLoad(C, A);                  // the original test 18: A, reload, then C
    }

    /** A CommandProcessor in batch mode whose output we can read back command by command. */
    static final class Session {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final CommandProcessor cp = new CommandProcessor(new PrintStream(buf, true), null);
        String run(String command) {
            buf.reset();
            cp.execute(command);
            return buf.toString();
        }
    }

    private static void assertFreshLoad(String scenario, String mutateWith) {
        Session cleanS = new Session();
        String loadOut = cleanS.run("LOAD " + CSV);
        assertTrue(loadOut.startsWith("Loaded ") && !loadOut.startsWith("Loaded 0 "), "official CSV not loaded: " + loadOut);
        CommandProcessor clean = cleanS.cp;
        String cleanReport = cleanS.run(scenario);
        String cleanState = snapshot(clean);
        assertTrue(clean.scheduler().checkConsistency(clean.registry()).isEmpty(),
                "registry and BSTs out of sync after a clean run of: " + scenario);

        Session dirtyS = new Session();
        CommandProcessor dirty = dirtyS.cp;
        dirtyS.run("LOAD " + CSV);
        dirtyS.run(mutateWith);                 // mutate state with another scenario ...
        dirtyS.run("LOAD " + CSV);              // ... then reset
        String dirtyReport = dirtyS.run(scenario);
        String dirtyState = snapshot(dirty);

        assertFalse(cleanReport.startsWith("ERROR"), "scenario failed: " + cleanReport);
        assertEquals(cleanReport, dirtyReport, "report of [" + scenario + "] differs after [" + mutateWith + "] + LOAD");
        assertEquals(cleanState, dirtyState, "registry state after [" + scenario + "] differs after [" + mutateWith + "] + LOAD");
        List<String> problems = dirty.scheduler().checkConsistency(dirty.registry());
        assertTrue(problems.isEmpty(), "registry and BSTs out of sync: " + problems);
    }

    /** Every flight as "id|runway|start|end|status", in ID order (our radix sort, no library sort). */
    static String snapshot(CommandProcessor cp) {
        List<Flight> list = new ArrayList<>();
        for (Flight f : cp.registry().all()) list.add(f);
        Flight[] sorted = RadixSort.byId(list.toArray(new Flight[0]));
        StringBuilder sb = new StringBuilder();
        for (Flight f : sorted)
            sb.append(f.id).append('|').append(f.runway).append('|').append(f.start).append('|')
              .append(f.end).append('|').append(f.status).append('\n');
        return sb.toString();
    }
}
