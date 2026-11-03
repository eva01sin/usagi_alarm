package com.usagi.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class HolidayPolicyTest {
    private static final KoreanHolidays BUILT_IN = new KoreanHolidays(date -> 0);
    private static final LocalDate FROM = LocalDate.of(2026, 5, 20);

    private static long utc(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static Set<LocalDate> days(LocalDate... values) {
        Set<LocalDate> set = new HashSet<>();
        for (LocalDate value : values) set.add(value);
        return set;
    }

    @Test public void oneDayAllDayEventEndIsExclusive() {
        Set<LocalDate> out = new HashSet<>();
        CalendarHolidays.addAllDay(out, utc(2026, 6, 3), utc(2026, 6, 4));
        assertEquals(days(LocalDate.of(2026, 6, 3)), out);
    }

    @Test public void multiDayEventCoversEveryDayButNotEnd() {
        Set<LocalDate> out = new HashSet<>();
        CalendarHolidays.addAllDay(out, utc(2026, 9, 24), utc(2026, 9, 27));
        assertEquals(days(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26)), out);
    }

    @Test public void zeroLengthEventCountsAsOneDay() {
        Set<LocalDate> out = new HashSet<>();
        CalendarHolidays.addAllDay(out, utc(2026, 6, 3), utc(2026, 6, 3));
        assertEquals(days(LocalDate.of(2026, 6, 3)), out);
    }

    @Test public void utcMidnightStaysOnSameDate() {
        assertEquals(LocalDate.of(2026, 6, 3), CalendarHolidays.utcDate(utc(2026, 6, 3)));
    }

    @Test public void holidayCalendarOwnerMatchesBothPrefixes() {
        assertTrue(CalendarHolidays.isHolidayCalendarOwner("ko.south_korea#holiday@group.v.calendar.google.com"));
        assertTrue(CalendarHolidays.isHolidayCalendarOwner("en.south_korea#holiday@group.v.calendar.google.com"));
        assertFalse(CalendarHolidays.isHolidayCalendarOwner("someone@gmail.com"));
        assertFalse(CalendarHolidays.isHolidayCalendarOwner(null));
    }

    @Test public void calendarDayIsHolidayOnlyWhenCalendarIsUsed() {
        LocalDate election = LocalDate.of(2026, 6, 3);
        HolidayPolicy with = new HolidayPolicy(BUILT_IN, (from, to) -> days(election), FROM);
        HolidayPolicy without = new HolidayPolicy(BUILT_IN, null, FROM);
        assertTrue(with.isHoliday(election));
        assertFalse(without.isHoliday(election));
        assertFalse(with.isHoliday(election.plusDays(1)));
    }

    @Test public void builtInHolidayStillAppliesWithCalendar() {
        HolidayPolicy policy = new HolidayPolicy(BUILT_IN, (from, to) -> days(), FROM);
        assertTrue(policy.isHoliday(LocalDate.of(2026, 7, 17)));
        assertFalse(policy.isHoliday(LocalDate.of(2026, 7, 16)));
    }

    @Test public void failingCalendarFallsBackToBuiltIn() {
        HolidayPolicy policy = new HolidayPolicy(BUILT_IN, (from, to) -> { throw new SecurityException("revoked"); }, FROM);
        assertTrue(policy.isHoliday(LocalDate.of(2026, 7, 17)));
        assertFalse(policy.isHoliday(LocalDate.of(2026, 6, 3)));
    }

    @Test public void calendarIsReadOnceForTheRange() {
        int[] reads = {0};
        HolidayPolicy policy = new HolidayPolicy(BUILT_IN, (from, to) -> {
            reads[0]++;
            assertEquals(FROM, from);
            assertEquals(FROM.plusDays(HolidayPolicy.RANGE_DAYS), to);
            return days();
        }, FROM);
        for (int i = 0; i < 30; i++) policy.isHoliday(FROM.plusDays(i));
        assertEquals(1, reads[0]);
    }

    @Test public void alarmSkipsCalendarHolidayForRepeatAndOneShot() {
        java.time.ZoneId seoul = java.time.ZoneId.of("Asia/Seoul");
        LocalDate election = LocalDate.of(2026, 6, 3); // 수요일
        HolidayPolicy policy = new HolidayPolicy(BUILT_IN, (from, to) -> days(election), FROM);
        long after = LocalDate.of(2026, 6, 2).atTime(9, 0).atZone(seoul).toInstant().toEpochMilli();
        long expected = LocalDate.of(2026, 6, 4).atTime(8, 0).atZone(seoul).toInstant().toEpochMilli();
        assertEquals(expected, AlarmTimes.next(8, 0, 0, after, seoul, policy::isHoliday));
        assertEquals(expected, AlarmTimes.next(8, 0, 0b0011111, after, seoul, policy::isHoliday));
    }
}
