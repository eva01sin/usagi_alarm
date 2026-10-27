package com.usagi.alarm;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Bit 0 = Monday. No selected days means a one-shot alarm. */
public final class AlarmTimes {
    private AlarmTimes() {}

    public static long next(int hour, int minute, int days, long after, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate date = today.plusDays(offset);
            int bit = 1 << (date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
            if (days != 0 && (days & bit) == 0) continue;
            long candidate = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
            if (candidate > after) return candidate;
        }
        throw new IllegalArgumentException("No matching alarm day");
    }
}
