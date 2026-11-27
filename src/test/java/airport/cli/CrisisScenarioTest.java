package airport.cli;

import static org.junit.jupiter.api.Assertions.*;

import airport.model.Flight;
import airport.model.FlightStatus;
import airport.model.TimeUtil;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests 9, 10, 11, 12 and 16 on the hand-made CSV src/test/resources/p3_small.csv. */
class CrisisScenarioTest {
    private static final String CSV = "src/test/resources/p3_small.csv";
    private ByteArrayOutputStream buf;
    private CommandProcessor p;

    private static long t(String hhmm) { return TimeUtil.parse("2026-11-27 " + hhmm); }

    @BeforeEach
    void freshLoad() {
        buf = new ByteArrayOutputStream();
        p = new CommandProcessor(new PrintStream(buf), null);
        p.execute("LOAD " + CSV);
    }

    private Flight f(String id) { return p.registry().get(id); }

    private void assertConsistent() {
        assertTrue(p.scheduler().checkConsistency(p.registry()).isEmpty());
    }

    // Test 9
    @Test
    void scenarioA_closure() {
        p.execute("RUNWAY_CLOSURE R1 \"2026-11-27 09:00\" \"2026-11-27 11:00\"");
        for (String id : new String[] {"F1", "F2", "F3", "F4"}) {          // F1 via predecessor rule
            Flight x = f(id);
            if (x.status == FlightStatus.DELAYED) assertTrue(x.start - x.originalStart <= 120, id);
            else assertEquals(FlightStatus.CANCELLED, x.status, id);
        }
        // not affected
        assertEquals(FlightStatus.SCHEDULED, f("F5").status);
        assertEquals(t("11:00"), f("F5").start);
        assertEquals(t("09:00"), f("G1").start);
        assertEquals(FlightStatus.SCHEDULED, f("G1").status);
        assertTrue(p.scheduler().conflicts("R1", t("11:00"), t("11:05")).size() == 1);  // only F5, no overlap
        assertConsistent();
    }

    // Test 12: order + 120-min cap
    @Test
    void recoveryOrderAndCap() {
        p.execute("RUNWAY_CLOSURE R1 \"2026-11-27 09:00\" \"2026-11-27 11:00\"");
        // priority 1 (F2) is placed first: first free slot after F5 (11:00-11:05) is 11:05
        assertEquals(FlightStatus.DELAYED, f("F2").status);
        assertEquals(t("11:05"), f("F2").start);
        // F1 (10 min, orig 08:55) needs a start <= 10:55: impossible after 11:00 -> cancelled
        assertEquals(FlightStatus.CANCELLED, f("F1").status);
        assertNotNull(p.registry().get("F1"));
        assertConsistent();
    }

    // Test 10
    @Test
    void scenarioB_conflictAndOffer() {
        p.execute("EMERGENCY_INSERT EM1 EM JFK BOS R1 \"2026-11-27 09:05\" \"2026-11-27 09:10\" 1");
        String out = buf.toString();
        assertTrue(out.contains("CONFLICT with: F2"), out);
        assertTrue(out.contains("Earliest free slot"), out);
        assertNull(p.registry().get("EM1"));                    // batch mode without --accept: not inserted
        assertEquals(t("09:05"), f("F2").start);                // nobody moved

        p.execute("EMERGENCY_INSERT EM1 EM JFK BOS R1 \"2026-11-27 09:05\" \"2026-11-27 09:10\" 1 --accept");
        Flight em = f("EM1");
        assertNotNull(em);
        assertTrue(p.scheduler().conflicts("R1", em.start, em.end).size() == 1);   // only itself
        assertEquals(t("09:05"), f("F2").start);
        assertConsistent();
    }

    // Test 11
    @Test
    void scenarioC_massCancel() {
        p.execute("MASS_CANCEL \"2026-11-27 14:00\" \"2026-11-27 15:00\"");
        for (String id : new String[] {"H1", "H2"}) {
            assertEquals(FlightStatus.CANCELLED, f(id).status);
            assertNotNull(p.registry().get(id));                // FIND still works
        }
        assertTrue(p.scheduler().tree("R1").rangeSearch(t("14:00"), t("15:00")).isEmpty());
        assertTrue(p.scheduler().tree("R2").rangeSearch(t("14:00"), t("15:00")).isEmpty());
        assertEquals(FlightStatus.SCHEDULED, f("H3").status);   // starts exactly at 15:00: untouched
        assertConsistent();
    }

    // Test 16
    @Test
    void consistencyAfterEveryMutation() {
        p.execute("ADD N1 XX AAA BBB R2 \"2026-11-27 10:00\" \"2026-11-27 10:05\" 2");
        assertNotNull(f("N1")); assertConsistent();
        p.execute("RESCHEDULE N1 \"2026-11-27 10:30\"");
        assertEquals(t("10:30"), f("N1").start); assertConsistent();
        p.execute("CANCEL N1");
        assertEquals(FlightStatus.CANCELLED, f("N1").status); assertConsistent();
        p.execute("MASS_CANCEL \"2026-11-27 14:00\" \"2026-11-27 15:00\"");
        assertConsistent();
    }

    @Test
    void badInputGivesErrorsNotStackTraces() {
        for (String bad : new String[] {"FOO", "FIND", "FIND NOPE", "CHECK_CONFLICT R9 \"2026-11-27 09:00\" \"2026-11-27 09:05\"",
                "CHECK_CONFLICT R1 \"2026-13-01 09:00\" \"2026-11-27 09:05\"",
                "ADD F1 A B C R1 \"2026-11-27 12:00\" \"2026-11-27 12:05\" 1",
                "ADD Z1 A B C R1 \"2026-11-27 12:05\" \"2026-11-27 12:00\" 1",
                "ADD Z1 A B C R1 \"2026-11-27 12:00\" \"2026-11-27 12:05\" 4"}) {
            buf.reset();
            p.execute(bad);
            assertTrue(buf.toString().startsWith("ERROR:"), bad + " -> " + buf);
        }
    }

    @Test
    void tokenizerKeepsQuotedTimesTogether() {
        List<String> t = CommandProcessor.tokenize("CHECK_CONFLICT R1 \"2026-11-27 09:00\" \"2026-11-27 09:05\"");
        assertEquals(4, t.size());
        assertEquals("2026-11-27 09:00", t.get(2));
    }
}
