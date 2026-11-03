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

    // 공휴일 건너뛰기: 2026-05-25(월)는 부처님오신날(일요일) 대체공휴일, 2026-09-24~26은 추석 연휴.
    private final java.util.function.Predicate<java.time.LocalDate> holiday = date -> {
        java.time.LocalDate[] days = {
                java.time.LocalDate.of(2026, 5, 25), java.time.LocalDate.of(2026, 9, 24), java.time.LocalDate.of(2026, 9, 25),
                java.time.LocalDate.of(2026, 9, 26), java.time.LocalDate.of(2028, 10, 2), java.time.LocalDate.of(2028, 10, 3),
                java.time.LocalDate.of(2028, 10, 4), java.time.LocalDate.of(2028, 10, 5), java.time.LocalDate.of(2028, 10, 9)};
        return java.util.Arrays.asList(days).contains(date);
    };

    @Test public void weeklyAlarmSkipsHolidayMonday() {
        // 월요일 08:00 반복. 2026-05-18 아침 이후 -> 5/25는 휴일이라 6/1(월) 08:00 KST
        long after = at("2026-05-17T23:30:00Z"); // 5/18 08:30 KST
        assertEquals(at("2026-05-31T23:00:00Z"), AlarmTimes.next(8, 0, 1, after, seoul, holiday));
        assertEquals(at("2026-05-24T23:00:00Z"), AlarmTimes.next(8, 0, 1, after, seoul));
        assertEquals(at("2026-05-24T23:00:00Z"), AlarmTimes.next(8, 0, 1, after, seoul, null));
    }

    @Test public void oneShotBeforeChuseokMovesToFirstNonHoliday() {
        // 2026-09-23 09:00 KST 이후 오전 8시 일회성 알람: 9/24~26 휴일 -> 9/27(일) 08:00 KST
        long after = at("2026-09-23T00:00:00Z");
        assertEquals(at("2026-09-26T23:00:00Z"), AlarmTimes.next(8, 0, 0, after, seoul, holiday));
        assertEquals(at("2026-09-23T23:00:00Z"), AlarmTimes.next(8, 0, 0, after, seoul));
    }

    @Test public void fiveDayChainNeedsWideSearch() {
        // 2028-10-02~05 휴일 + 주말 + 10/9 휴일: 평일 반복(월~금)은 10/6(금)에, 10/6 이후에는 10/10(화)에 울린다.
        int weekdays = 31;
        assertEquals(at("2028-10-05T23:00:00Z"), AlarmTimes.next(8, 0, weekdays, at("2028-10-01T00:00:00Z"), seoul, holiday));
        assertEquals(at("2028-10-09T23:00:00Z"), AlarmTimes.next(8, 0, weekdays, at("2028-10-06T00:00:00Z"), seoul, holiday));
    }

    @Test public void weeklyAlarmSkipsLaborDay() {
        // 금요일 08:00 반복. 2026-04-24 09:00 KST 이후 -> 5/1(노동절)은 건너뛰고 5/8(금)
        KoreanHolidays korea = new KoreanHolidays(date -> 0);
        assertEquals(at("2026-05-07T23:00:00Z"), AlarmTimes.next(8, 0, 16, at("2026-04-24T00:00:00Z"), seoul, korea::isHoliday));
        assertEquals(at("2026-04-30T23:00:00Z"), AlarmTimes.next(8, 0, 16, at("2026-04-24T00:00:00Z"), seoul));
    }

    @Test public void skipPredicateThatRejectsEverythingStillThrows() {
        try {
            AlarmTimes.next(8, 0, 0, at("2026-10-27T00:00:00Z"), seoul, date -> true);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("No matching alarm day", expected.getMessage());
        }
    }

    @Test public void springDstGapUsesNextValidTime() {
        assertEquals(at("2026-03-08T07:30:00Z"), AlarmTimes.next(2, 30, 0, at("2026-03-08T05:00:00Z"), ZoneId.of("America/New_York")));
    }
}
