package com.usagi.alarm;

import org.junit.Test;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** 음력 공급원은 알려진 명절 날짜 표(2026~2028)로 대신해 대체공휴일 논리를 검증한다. */
public class KoreanHolidaysTest {
    private static final Map<LocalDate, Integer> LUNAR = new HashMap<>();
    static {
        // 설날(음력 1/1) 당일
        LUNAR.put(LocalDate.of(2026, 2, 17), KoreanHolidays.LUNAR_NEW_YEAR);
        LUNAR.put(LocalDate.of(2027, 2, 7), KoreanHolidays.LUNAR_NEW_YEAR);
        LUNAR.put(LocalDate.of(2028, 1, 27), KoreanHolidays.LUNAR_NEW_YEAR);
        // 추석(음력 8/15) 당일
        LUNAR.put(LocalDate.of(2026, 9, 25), KoreanHolidays.CHUSEOK);
        LUNAR.put(LocalDate.of(2027, 9, 15), KoreanHolidays.CHUSEOK);
        LUNAR.put(LocalDate.of(2028, 10, 3), KoreanHolidays.CHUSEOK);
        // 부처님오신날(음력 4/8)
        LUNAR.put(LocalDate.of(2026, 5, 24), KoreanHolidays.BUDDHAS_BIRTHDAY);
        LUNAR.put(LocalDate.of(2027, 5, 13), KoreanHolidays.BUDDHAS_BIRTHDAY);
        LUNAR.put(LocalDate.of(2028, 5, 2), KoreanHolidays.BUDDHAS_BIRTHDAY);
    }
    private final KoreanHolidays holidays = new KoreanHolidays(date -> LUNAR.getOrDefault(date, 0));

    private void assertHolidays(boolean expected, int year, int month, int... days) {
        for (int day : days) {
            LocalDate date = LocalDate.of(year, month, day);
            assertEquals(date.toString(), expected, holidays.isHoliday(date));
        }
    }

    @Test public void fixedHolidays() {
        assertHolidays(true, 2027, 1, 1);
        assertHolidays(true, 2027, 3, 1);
        assertHolidays(true, 2027, 5, 5);
        assertHolidays(true, 2027, 6, 6);
        assertHolidays(true, 2027, 8, 15);
        assertHolidays(true, 2027, 10, 3);
        assertHolidays(true, 2027, 10, 9);
        assertHolidays(true, 2027, 12, 25);
        assertHolidays(false, 2027, 4, 5);
    }

    @Test public void lunarNewYearIsThreeDays() {
        assertHolidays(false, 2026, 2, 15);
        assertHolidays(true, 2026, 2, 16, 17, 18);
        assertHolidays(false, 2026, 2, 19);
    }

    @Test public void chuseokIsThreeDaysWithoutSubstituteWhenNoOverlap() {
        assertHolidays(false, 2026, 9, 23);
        assertHolidays(true, 2026, 9, 24, 25, 26);
        assertHolidays(false, 2026, 9, 27, 28, 29);
    }

    @Test public void lunarNewYearOnSundayAddsSubstituteAfterChain() {
        // 2027 설날 연휴 2/6(토)~2/8(월): 일요일이 겹쳐 2/9(화)가 대체공휴일
        assertHolidays(true, 2027, 2, 6, 7, 8, 9);
        assertHolidays(false, 2027, 2, 5, 10);
    }

    @Test public void chuseokOverlappingNationalFoundationDayAddsSubstitute() {
        // 2028 추석 연휴 10/2~10/4가 개천절(10/3)과 겹쳐 10/5(목)이 대체공휴일, 한글날 10/9(월)
        assertHolidays(true, 2028, 10, 2, 3, 4, 5, 9);
        assertHolidays(false, 2028, 10, 6, 10);
    }

    @Test public void saturdayOrSundayHolidayMovesToNextWeekday() {
        assertHolidays(true, 2026, 3, 1, 2);       // 3·1절 일요일 -> 월요일
        assertHolidays(true, 2026, 5, 24, 25);     // 부처님오신날 일요일 -> 월요일
        assertHolidays(true, 2026, 8, 15, 17);     // 광복절 토요일 -> 월요일(일요일은 평일 아님)
        assertHolidays(false, 2026, 8, 16);
        assertHolidays(true, 2026, 10, 3, 5);      // 개천절 토요일 -> 월요일
        assertHolidays(false, 2026, 10, 6);
    }

    @Test public void memorialDayAndNewYearHaveNoSubstitute() {
        assertHolidays(true, 2026, 6, 6);          // 토요일 현충일
        assertHolidays(false, 2026, 6, 8);
        assertHolidays(true, 2028, 1, 1);          // 토요일 신정
        assertHolidays(false, 2028, 1, 3);
    }

    @Test public void weekdayHolidaysHaveNoSubstitute() {
        // 2027-05-05(수), 2028-05-05(금), 2028-05-02(화 부처님오신날)는 평일이라 다음 날이 공휴일이 되지 않는다.
        assertHolidays(false, 2027, 5, 6);
        assertHolidays(false, 2028, 5, 3, 6);
        assertHolidays(true, 2028, 5, 2, 5);
    }

    @Test public void laborDayAndConstitutionDayAreHolidaysFrom2026() {
        assertHolidays(true, 2026, 5, 1);
        assertHolidays(true, 2026, 7, 17);
        assertHolidays(true, 2027, 5, 1, 3);      // 토요일 -> 월요일 대체
        assertHolidays(true, 2027, 7, 17, 19);
        assertHolidays(true, 2028, 5, 1);
        assertHolidays(true, 2028, 7, 17);
        assertHolidays(false, 2025, 5, 1);
        assertHolidays(false, 2025, 7, 17);
    }

    @Test public void substituteHolidaysIn2027() {
        assertHolidays(true, 2027, 2, 9);
        assertHolidays(true, 2027, 5, 3);
        assertHolidays(true, 2027, 7, 19);
        assertHolidays(true, 2027, 8, 16);
        assertHolidays(true, 2027, 10, 4, 11);
        assertHolidays(true, 2027, 12, 27);
        // 대체공휴일 바로 다음 평일은 휴일이 아니다.
        assertHolidays(false, 2027, 2, 10);
        assertHolidays(false, 2027, 5, 4);
        assertHolidays(false, 2027, 7, 20);
        assertHolidays(false, 2027, 8, 17);
        assertHolidays(false, 2027, 10, 5, 12);
        assertHolidays(false, 2027, 12, 28);
    }

    @Test public void electionDaysAreNotBuiltIn() {
        // 선거일은 내장하지 않고 캘린더에서만 가져온다.
        assertHolidays(false, 2026, 6, 3);
        assertHolidays(false, 2028, 4, 12);
        assertHolidays(false, 2026, 6, 4);
    }

    @Test public void sundaysAndOrdinaryDaysAreNotHolidays() {
        assertHolidays(false, 2026, 11, 3, 4, 8);
    }

    @Test public void unknownLunarDateIsNotHoliday() {
        KoreanHolidays noLunar = new KoreanHolidays(date -> 0);
        assertFalse(noLunar.isHoliday(LocalDate.of(2026, 2, 17)));
        assertTrue(noLunar.isHoliday(LocalDate.of(2026, 3, 1)));
    }
}
