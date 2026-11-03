package com.usagi.alarm;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Predicate;

/** Bit 0 = Monday. No selected days means a one-shot alarm. */
public final class AlarmTimes {
    private static final int NORMAL_SEARCH_DAYS = 7;
    // 추석 등 연속 휴일(주말 포함 5일 이상)을 넘길 수 있도록 날짜를 건너뛸 때는 탐색 범위를 넓힌다.
    private static final int SKIPPING_SEARCH_DAYS = 40;

    private AlarmTimes() {}

    public static long next(int hour, int minute, int days, long after, ZoneId zone) {
        return next(hour, minute, days, after, zone, null);
    }

    /**
     * 다음 울림 시각. skipDate가 있으면 그 날짜(예: 한국 공휴일)는 건너뛰고 요일 조건에 맞는 다음 날짜로 정한다.
     * 일회성 알람(days == 0)도 건너뛴 날짜 다음의 첫 날짜로 옮긴다. skipDate가 null이면 기존 동작과 같다.
     * 조건에 맞는 날이 탐색 범위에 없으면 IllegalArgumentException.
     */
    public static long next(int hour, int minute, int days, long after, ZoneId zone, Predicate<LocalDate> skipDate) {
        LocalDate today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        int searchDays = skipDate == null ? NORMAL_SEARCH_DAYS : SKIPPING_SEARCH_DAYS;
        for (int offset = 0; offset <= searchDays; offset++) {
            LocalDate date = today.plusDays(offset);
            int bit = 1 << (date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
            if (days != 0 && (days & bit) == 0) continue;
            if (skipDate != null && skipDate.test(date)) continue;
            long candidate = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
            if (candidate > after) return candidate;
        }
        throw new IllegalArgumentException("No matching alarm day");
    }
}
