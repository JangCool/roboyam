package com.community.notificationtemplate.catalog;

import java.util.List;
import java.util.Locale;

/** 문구 언어 — 화면 언어와 같은 두 가지({@code ko} · {@code en}) */
public final class MessageLocales {

    public static final String KO = "ko";
    public static final String EN = "en";
    public static final List<String> ALL = List.of(KO, EN);

    private MessageLocales() {
    }

    public static boolean valid(String locale) {
        return KO.equals(locale) || EN.equals(locale);
    }

    /** 받는 사람 언어 → 문구 언어. 모르는 언어·없음은 null(부르는 쪽이 기본 언어로 본다) */
    public static String of(Locale locale) {
        if (locale == null) {
            return null;
        }
        String language = locale.getLanguage();
        return valid(language) ? language : null;
    }

    /** 글자 언어 태그 → 문구 언어. 모르는 값은 null */
    public static String of(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return of(Locale.forLanguageTag(tag.strip()));
    }

    public static Locale locale(String locale) {
        return EN.equals(locale) ? Locale.ENGLISH : Locale.KOREAN;
    }

    /** 이력 칸 이름의 언어 꼬리({@code Ko} · {@code En}) */
    public static String suffix(String locale) {
        return EN.equals(locale) ? "En" : "Ko";
    }
}
