package airport.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public final class TimeUtil {
    private static final DateTimeFormatter F =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private TimeUtil() {}

    /** Throws IllegalArgumentException on bad input. */
    public static long parse(String s) {
        try {
            LocalDateTime t = LocalDateTime.parse(s.trim(), F);
            return t.toEpochSecond(ZoneOffset.UTC) / 60;
        } catch (Exception e) {
            throw new IllegalArgumentException("bad timestamp: " + s);
        }
    }

    public static String format(long minutes) {
        return LocalDateTime.ofEpochSecond(minutes * 60, 0, ZoneOffset.UTC).format(F);
    }
}