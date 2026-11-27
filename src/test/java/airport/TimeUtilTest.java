package airport;

import airport.model.TimeUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeUtilTest {
    @Test
    void roundTrip() {
        assertEquals("2026-11-27 09:00", TimeUtil.format(TimeUtil.parse("2026-11-27 09:00")));
    }

    @Test
    void badInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.parse("2026-13-01 09:00"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.parse("2026-11-27 9:0"));
    }
}
