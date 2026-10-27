package com.usagi.alarm;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class AlarmTimesTest {
    private final ZoneId seoul = ZoneId.of("Asia/Seoul");
    private long at(String instant) { return Instant.parse(instant).toEpochMilli(); }

    @Test public void oneShotUsesTomorrowWhenTimeHasPassed() {
        assertEquals(at("2026-10-28T00:00:00Z"), AlarmTimes.next(9, 0, 0, at("2026-10-27T01:00:00Z"), seoul));
    }

    @Test public void weekdayMaskSkipsWeekend() {
        assertEquals(at("2026-11-01T23:00:00Z"), AlarmTimes.next(8, 0, 31, at("2026-10-30T23:00:00Z"), seoul));
    }

    @Test public void sameDayNextWeekWhenAlreadyFired() {
        assertEquals(at("2026-11-02T23:00:00Z"), AlarmTimes.next(8, 0, 2, at("2026-10-26T23:00:00Z"), seoul));
    }

    @Test public void zoneChangeUsesLocalTime() {
        assertEquals(at("2026-10-27T08:00:00Z"), AlarmTimes.next(8, 0, 0, at("2026-10-27T00:00:00Z"), ZoneId.of("UTC")));
    }

    @Test public void springDstGapUsesNextValidTime() {
        assertEquals(at("2026-03-08T07:30:00Z"), AlarmTimes.next(2, 30, 0, at("2026-03-08T05:00:00Z"), ZoneId.of("America/New_York")));
    }
}
