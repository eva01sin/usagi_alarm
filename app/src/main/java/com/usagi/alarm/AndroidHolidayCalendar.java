package com.usagi.alarm;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 기기 캘린더 제공자에서 구글 대한민국 공휴일 캘린더의 종일 일정을 읽는다. 읽기 권한이 없거나 철회됐거나
 * 캘린더가 없거나 읽다가 오류가 나면 빈 집합을 돌려주어 호출한 쪽이 내장 계산만 쓰게 한다.
 */
final class AndroidHolidayCalendar implements CalendarHolidays.Source {
    private final Context context;

    AndroidHolidayCalendar(Context context) { this.context = context.getApplicationContext(); }

    static boolean permitted(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** 공휴일 캘린더가 기기에 동기화돼 있는지. 권한이 없거나 오류가 나면 false. */
    boolean hasHolidayCalendar() { return !calendarIds().isEmpty(); }

    @Override public Set<LocalDate> holidays(LocalDate from, LocalDate toExclusive) {
        Set<LocalDate> days = new HashSet<>();
        try {
            List<Long> ids = calendarIds();
            if (ids.isEmpty()) return days;
            // 종일 일정은 UTC 자정 기준이다. 경계 일정을 놓치지 않게 앞뒤로 하루씩 더 읽고, 날짜 변환은 UTC로 한다.
            long begin = from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long end = toExclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri, begin);
            ContentUris.appendId(uri, end);
            StringBuilder where = new StringBuilder(CalendarContract.Instances.ALL_DAY + "=1 AND "
                    + CalendarContract.Instances.CALENDAR_ID + " IN (");
            for (int i = 0; i < ids.size(); i++) where.append(i == 0 ? "" : ",").append(ids.get(i).longValue());
            where.append(")");
            ContentResolver resolver = context.getContentResolver();
            try (Cursor cursor = resolver.query(uri.build(),
                    new String[] {CalendarContract.Instances.BEGIN, CalendarContract.Instances.END},
                    where.toString(), null, null)) {
                if (cursor == null) return days;
                while (cursor.moveToNext()) CalendarHolidays.addAllDay(days, cursor.getLong(0), cursor.getLong(1));
            }
        } catch (RuntimeException e) {
            // SecurityException(권한 철회) 등: 캘린더 없이 내장 계산만 쓴다.
            return Collections.emptySet();
        }
        return days;
    }

    private List<Long> calendarIds() {
        List<Long> ids = new ArrayList<>();
        if (!permitted(context)) return ids;
        try (Cursor cursor = context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                new String[] {CalendarContract.Calendars._ID, CalendarContract.Calendars.OWNER_ACCOUNT}, null, null, null)) {
            if (cursor == null) return ids;
            while (cursor.moveToNext()) {
                if (CalendarHolidays.isHolidayCalendarOwner(cursor.getString(1))) ids.add(cursor.getLong(0));
            }
        } catch (RuntimeException e) {
            ids.clear();
        }
        return ids;
    }
}
