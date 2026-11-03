package com.usagi.alarm;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 한국 공휴일 판정. 알람이 "공휴일엔 울리지 않기" 설정일 때 {@link AlarmTimes}가 날짜를 건너뛰는 기준으로 쓴다.
 * Android에 의존하지 않으며, 음력 날짜는 {@link LunarCalendar}로 주입받는다(기기에서는 {@link AndroidLunarCalendar},
 * 단위 테스트에서는 알려진 날짜 표). 기기 지역·언어와 무관하게 항상 한국 달력을 기준으로 한다.
 *
 * <p>포함: 양력 고정 공휴일, 설날·추석 연휴(전날·당일·다음날), 부처님오신날, 대체공휴일.
 * 임시공휴일·선거일은 내장하지 않고, 사용자가 켠 경우에만 {@link CalendarHolidays}가 캘린더에서 가져온다.
 * 대체공휴일 규칙: 설날·추석 연휴가 일요일이나 다른 공휴일과 겹치면 연휴 다음의 첫 평일 하루,
 * 3·1절/노동절/어린이날/제헌절/부처님오신날/광복절/개천절/한글날/성탄절이 토·일요일이면 그 다음 첫 평일.
 * 현충일(6/6)과 신정(1/1)은 대체공휴일이 없다. 대체공휴일 날짜는 다른 공휴일·이미 정해진 대체공휴일과 겹치지 않는다.
 */
public final class KoreanHolidays {
    /** 음력 날짜 공급원. 구현은 스레드 안전해야 한다. */
    public interface LunarCalendar {
        /**
         * 양력 날짜의 음력 월·일을 월*100+일(예: 음력 8월 15일 = 815)로 돌려준다.
         * 윤달이거나 모르는 날짜는 0을 돌려준다(윤달에는 명절이 없으므로 공휴일로 보지 않는다).
         */
        int lunarMonthDay(LocalDate date);
    }

    static final int LUNAR_NEW_YEAR = 101;
    static final int BUDDHAS_BIRTHDAY = 408;
    static final int CHUSEOK = 815;
    /** 양력 월*100+일 형식의 고정 공휴일. */
    private static final int[] FIXED_HOLIDAYS = {101, 301, 501, 505, 606, 717, 815, 1003, 1009, 1225};
    /** 고정 공휴일 중 토·일요일과 겹치면 대체공휴일이 생기는 날(신정·현충일 제외). */
    private static final int[] FIXED_WITH_SUBSTITUTE = {301, 501, 505, 717, 815, 1003, 1009, 1225};
    /**
     * 노동절(5/1)·제헌절(7/17)은 2026년부터 공휴일(관공서의 공휴일에 관한 규정 개정, 2026-04-28 국무회의 의결)이며
     * 대체공휴일도 적용된다. 노동부는 노동절의 근로기준법상 휴일 대체는 적용되지 않는다고 보지만, 이 앱은 관공서 공휴일 규정을 따른다.
     */
    private static final int NEW_HOLIDAYS_FIRST_YEAR = 2026;
    private static final int[] HOLIDAYS_FROM_2026 = {501, 717};
    private static final int MAX_SUBSTITUTE_SEARCH_DAYS = 14;

    private final LunarCalendar lunar;
    private final Map<Integer, Set<LocalDate>> substitutesByYear = new HashMap<>();

    public KoreanHolidays(LunarCalendar lunar) { this.lunar = lunar; }

    /** 대체공휴일을 포함해 공휴일이면 true. */
    public boolean isHoliday(LocalDate date) {
        return isBaseHoliday(date) || isSubstituteHoliday(date);
    }

    /** 대체공휴일을 제외한 공휴일(고정·음력). */
    private boolean isBaseHoliday(LocalDate date) {
        return isFixedHoliday(date) || isBuddhasBirthday(date)
                || isAroundLunarDay(date, LUNAR_NEW_YEAR) || isAroundLunarDay(date, CHUSEOK);
    }

    private static int monthDay(LocalDate date) { return date.getMonthValue() * 100 + date.getDayOfMonth(); }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) if (candidate == value) return true;
        return false;
    }

    private static boolean isFixedHoliday(LocalDate date) {
        return contains(FIXED_HOLIDAYS, monthDay(date)) && isInForce(date);
    }

    private static boolean isFixedWithSubstitute(LocalDate date) {
        return contains(FIXED_WITH_SUBSTITUTE, monthDay(date)) && isInForce(date);
    }

    /** 2026년 이전에는 노동절·제헌절이 공휴일이 아니었다. */
    private static boolean isInForce(LocalDate date) {
        return date.getYear() >= NEW_HOLIDAYS_FIRST_YEAR || !contains(HOLIDAYS_FROM_2026, monthDay(date));
    }

    private boolean isBuddhasBirthday(LocalDate date) { return lunar.lunarMonthDay(date) == BUDDHAS_BIRTHDAY; }

    /** 설날·추석처럼 음력 날짜 당일과 그 전날·다음날(3일 연휴)이면 true. */
    private boolean isAroundLunarDay(LocalDate date, int lunarMonthDay) {
        return lunar.lunarMonthDay(date.minusDays(1)) == lunarMonthDay
                || lunar.lunarMonthDay(date) == lunarMonthDay
                || lunar.lunarMonthDay(date.plusDays(1)) == lunarMonthDay;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /** 대체공휴일이 연말에 정해지면 다음 해 초로 넘어갈 수 있어 전년 계산분도 함께 본다. */
    private boolean isSubstituteHoliday(LocalDate date) {
        return substitutesOf(date.getYear()).contains(date) || substitutesOf(date.getYear() - 1).contains(date);
    }

    private synchronized Set<LocalDate> substitutesOf(int year) {
        Set<LocalDate> cached = substitutesByYear.get(year);
        if (cached != null) return cached;
        Set<LocalDate> substitutes = new HashSet<>();
        // 날짜 순서대로 훑어, 앞선 대체공휴일이 뒤따르는 대체공휴일의 후보에서 빠지게 한다.
        for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1)) {
            int lunarMonthDay = lunar.lunarMonthDay(date);
            if (lunarMonthDay == LUNAR_NEW_YEAR || lunarMonthDay == CHUSEOK) addChainSubstitute(substitutes, date);
            boolean singleDayHoliday = isFixedWithSubstitute(date) || lunarMonthDay == BUDDHAS_BIRTHDAY;
            if (singleDayHoliday && isWeekend(date)) substitutes.add(nextFreeWeekday(substitutes, date));
        }
        substitutesByYear.put(year, substitutes);
        return substitutes;
    }

    /** 설날·추석 3일 연휴가 일요일이나 다른 공휴일과 겹치면 연휴 다음 첫 평일을 대체공휴일로 한다. */
    private void addChainSubstitute(Set<LocalDate> substitutes, LocalDate center) {
        boolean overlaps = false;
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate day = center.plusDays(offset);
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY || isOtherHolidayOnChainDay(day)) overlaps = true;
        }
        if (overlaps) substitutes.add(nextFreeWeekday(substitutes, center.plusDays(1)));
    }

    /** 연휴 안의 날이 연휴와 무관한 다른 공휴일(고정·부처님오신날)이기도 한지. */
    private boolean isOtherHolidayOnChainDay(LocalDate day) {
        return isFixedHoliday(day) || isBuddhasBirthday(day);
    }

    /** after 다음 날부터 공휴일도 대체공휴일도 아닌 첫 평일. 탐색 한도 안에 없으면 한도 날짜를 쓴다. */
    private LocalDate nextFreeWeekday(Set<LocalDate> substitutes, LocalDate after) {
        LocalDate candidate = after;
        for (int i = 0; i < MAX_SUBSTITUTE_SEARCH_DAYS; i++) {
            candidate = candidate.plusDays(1);
            if (!isWeekend(candidate) && !isBaseHoliday(candidate) && !substitutes.contains(candidate)) return candidate;
        }
        return candidate;
    }
}
