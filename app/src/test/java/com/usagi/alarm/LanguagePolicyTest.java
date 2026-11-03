package com.usagi.alarm;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

public class LanguagePolicyTest {
    private static String language(String choice, Locale system) {
        return LanguagePolicy.resolve(choice, system).getLanguage();
    }

    @Test public void systemChoiceFollowsKoreanDevice() {
        assertEquals("ko", language(LanguagePolicy.SYSTEM, Locale.KOREA));
    }

    @Test public void systemChoiceFallsBackToEnglishForEnglishDevice() {
        assertEquals("en", language(LanguagePolicy.SYSTEM, Locale.US));
    }

    @Test public void systemChoiceFallsBackToEnglishForUnsupportedDevice() {
        assertEquals("en", language(LanguagePolicy.SYSTEM, Locale.JAPAN));
    }

    @Test public void explicitEnglishWinsOverKoreanDevice() {
        assertEquals("en", language(LanguagePolicy.ENGLISH, Locale.KOREA));
    }

    @Test public void explicitKoreanWinsOverEnglishDevice() {
        assertEquals("ko", language(LanguagePolicy.KOREAN, Locale.US));
        assertEquals("ko", language(LanguagePolicy.KOREAN, Locale.JAPAN));
    }

    @Test public void missingOrUnknownChoiceFollowsSystem() {
        assertEquals("ko", language(null, Locale.KOREA));
        assertEquals("en", language("fr", Locale.FRANCE));
        assertEquals("ko", language("fr", Locale.KOREA));
    }
}
