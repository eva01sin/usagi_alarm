package com.usagi.alarm;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

/**
 * 설정 화면에서 고른 언어를 앱 어디서나 같은 방식으로 적용하는 한 곳.
 * Activity·Service는 attachBaseContext에서, 앱이 꺼진 상태에서 도는 위젯 갱신은 views()에서 wrap()을 거친다.
 * Android 13 LocaleManager 대신 Configuration을 직접 덮어써서 minSdk 26에서도 같게 동작한다.
 */
final class LocaleHelper {
    private LocaleHelper() {}

    /** 저장된 언어 선택(없으면 시스템 따라가기)을 Locale로 바꾼다. */
    static Locale resolve(Context context) {
        // 시스템 언어는 앱 설정의 영향을 받지 않는 Resources.getSystem()에서 읽는다.
        Locale system = Resources.getSystem().getConfiguration().getLocales().get(0);
        return LanguagePolicy.resolve(new AlarmStore(context).language(), system);
    }

    /** 설정 언어가 적용된 Context. 이미 적용된 Context를 다시 넣어도 결과는 같다. */
    static Context wrap(Context base) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(resolve(base));
        return base.createConfigurationContext(configuration);
    }

    /** 날짜·시각 서식에 쓸, localized Context의 Locale. */
    static Locale localeOf(Context localized) {
        return localized.getResources().getConfiguration().getLocales().get(0);
    }
}
