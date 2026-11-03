package com.usagi.alarm;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * 캘린더(구글 대한민국 공휴일)에서 가져온 공휴일의 공통 정의. Android에 의존하지 않아 단위 테스트에서 가짜 공급원을 쓴다.
 * 종일 일정은 UTC 자정 기준으로 저장되므로, 날짜 변환은 항상 UTC로 한다.
 */
final class CalendarHolidays {
    /** 구간의 캘린더 공휴일 공급원. 읽지 못하면 예외 없이 빈 집합을 돌려줘야 한다(그러면 내장 계산만 쓴다). */
    interface Source {
        /** from 이상, toExclusive 미만 날짜 중 캘린더에 공휴일로 등록된 날. */
        Set<LocalDate> holidays(LocalDate from, LocalDate toExclusive);
    }

    private CalendarHolidays() {}

    static LocalDate utcDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** 종일 일정 하나를 날짜로 펼친다. 종료 시각은 배타라 하루짜리는 시작일만, 끝이 시작 이하이면 시작일 하루로 본다. */
    static void addAllDay(Set<LocalDate> out, long beginUtcMillis, long endUtcMillis) {
        LocalDate start = utcDate(beginUtcMillis);
        LocalDate end = utcDate(endUtcMillis);
        if (!end.isAfter(start)) end = start.plusDays(1);
        for (LocalDate day = start; day.isBefore(end); day = day.plusDays(1)) out.add(day);
    }

    /** 공휴일 캘린더인지: 소유 계정에 {@code south_korea#holiday}가 들어 있으면(ko.·en. 접두사 모두) 맞다. */
    static boolean isHolidayCalendarOwner(String ownerAccount) {
        return ownerAccount != null && ownerAccount.contains("south_korea#holiday");
    }
}
