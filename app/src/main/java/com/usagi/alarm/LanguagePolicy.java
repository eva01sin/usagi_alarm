package com.usagi.alarm;

import java.util.Locale;

/**
 * 설정에서 고른 언어 값을 앱이 쓸 Locale로 바꾸는 규칙. Android 클래스에 의존하지 않아 단위 테스트할 수 있다.
 * 지원 언어는 한국어·영어뿐이며, 시스템 설정 따라가기는 기기 언어가 한국어일 때만 한국어, 그 외는 영어다.
 */
final class LanguagePolicy {
    static final String SYSTEM = "system", KOREAN = "ko", ENGLISH = "en";

    private LanguagePolicy() {}

    /** 명시 선택(ko/en)이 우선이고, 비어 있거나 알 수 없는 값은 시스템 설정 따라가기로 취급한다. */
    static Locale resolve(String choice, Locale systemLocale) {
        if (KOREAN.equals(choice)) return Locale.KOREAN;
        if (ENGLISH.equals(choice)) return Locale.ENGLISH;
        return KOREAN.equals(systemLocale.getLanguage()) ? Locale.KOREAN : Locale.ENGLISH;
    }
}
