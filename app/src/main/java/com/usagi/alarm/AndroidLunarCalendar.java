package com.usagi.alarm;

import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import android.icu.util.ULocale;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 기기에 내장된 ICU의 한국 음력(dangi) 달력으로 음력 날짜를 구한다. 네트워크·추가 데이터가 필요 없다(android.icu는 API 24+).
 * 기기 지역·언어와 무관하게 항상 한국 달력과 한국 시간대(Asia/Seoul)를 쓴다.
 * JVM 단위 테스트에서는 android.icu를 쓸 수 없어, 테스트는 {@link KoreanHolidays.LunarCalendar}의 표 구현을 쓴다.
 */
final class AndroidLunarCalendar implements KoreanHolidays.LunarCalendar {
    private static final String SEOUL = "Asia/Seoul";
    private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL), new ULocale("ko_KR@calendar=dangi"));

    /** ICU 달력은 스레드 안전하지 않아 호출을 직렬화한다. 정오 기준으로 계산해 시간대 경계 오차를 피한다. */
    @Override public synchronized int lunarMonthDay(LocalDate date) {
        calendar.setTimeInMillis(date.atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant().toEpochMilli());
        if (calendar.get(Calendar.IS_LEAP_MONTH) != 0) return 0;
        return (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DAY_OF_MONTH);
    }

    /** 점검용: 실제로 한국 음력 달력이 만들어졌는지(예: "dangi"). */
    synchronized String calendarType() { return calendar.getType(); }
}
