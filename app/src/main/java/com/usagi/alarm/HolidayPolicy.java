package com.usagi.alarm;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

/**
 * 알람을 건너뛸 날 판정: 내장 한국 공휴일 또는(캘린더 사용 시) 캘린더 공휴일.
 * 캘린더는 처음 판정할 때 [from, from+RANGE_DAYS) 구간을 한 번만 읽는다. 구간 밖 날짜는 내장 계산만 쓴다.
 */
final class HolidayPolicy {
    // AlarmTimes의 최대 탐색 범위(40일)보다 넉넉하게 잡는다.
    static final int RANGE_DAYS = 60;

    private final KoreanHolidays builtIn;
    private final CalendarHolidays.Source calendar;
    private final LocalDate from;
    private Set<LocalDate> calendarDays;

    /** calendar가 null이면 내장 계산만 쓴다. */
    HolidayPolicy(KoreanHolidays builtIn, CalendarHolidays.Source calendar, LocalDate from) {
        this.builtIn = builtIn;
        this.calendar = calendar;
        this.from = from;
    }

    boolean isHoliday(LocalDate date) {
        return builtIn.isHoliday(date) || isCalendarHoliday(date);
    }

    private synchronized boolean isCalendarHoliday(LocalDate date) {
        if (calendar == null) return false;
        if (calendarDays == null) {
            Set<LocalDate> loaded;
            try { loaded = calendar.holidays(from, from.plusDays(RANGE_DAYS)); }
            catch (RuntimeException e) { loaded = null; }
            calendarDays = loaded == null ? Collections.emptySet() : loaded;
        }
        return calendarDays.contains(date);
    }
}
