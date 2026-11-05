package com.community.emoticon.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 고정 규격 — 설정에서 바꾸지 않는 값(그리기 크기 · 제작 가이드 문구 · 업로드 검사와 묶여 있어 바꾸면 이미 승인된 팩이 규격을 어기게 된다). 계약 가정 7(로보얌콘 기준),
 * user-web {@code EmoticonRules} 의 고정 칸 · admin-web {@code UtilEmoticon.SPEC} 과 같은 값이다. 바꿀 수 있는 값(팩당 개수 · 보상 · 심사 대기 상한)은 설정({@code emoticon_settings}).
 * 그림 크기·프레임·용량은 V140 CHECK 로도 막는다.
 */
public final class EmoticonSpec {

    /** 그림 한 변(px) — 그림 190 + 둘레 5px 투명 */
    public static final int IMAGE_SIZE = 200;
    public static final int IMAGE_PADDING = 5;
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int RECOMMENDED_BYTES = 300 * 1024;
    public static final int MAX_FRAMES = 24;
    public static final int COVER_MAX_BYTES = 1024 * 1024;
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 20;
    public static final int LABEL_MIN = 1;
    public static final int LABEL_MAX = 10;
    public static final int MAX_TAGS = 5;
    public static final int TAG_MAX = 10;
    public static final int DESCRIPTION_MAX = 200;
    /** 승인된 팩의 가격 변경 요청 메모 */
    public static final int PRICE_MEMO_MAX = 200;
    public static final int DISPLAY_SIZE = 100;
    public static final int DISPLAY_SIZE_MOBILE = 80;
    public static final int PICKER_SIZE = 64;
    /** 반려·판매 중단 사유 글자 수(작가에게 그대로 보인다) */
    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 200;
    /** 본문 하나의 토큰 상한(계약 가정 10) */
    public static final int TOKENS_PER_BODY = 20;
    /** 작가 신청 동의 셋의 문구 판(문구가 바뀌어도 무엇에 동의했는지 남긴다 — 문구를 바꾸면 이 값도 바꾼다) */
    public static final String CONSENT_VERSION = "2026-11-05";
    /** 팩 이름 — 한글·영문(숫자·띄어쓰기 허용). user-web {@code UtilEmoticonSpec.NAME_PATTERN} 과 같다 */
    public static final Pattern NAME_PATTERN = Pattern.compile("^[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ ]+$");

    private EmoticonSpec() {
    }

    /** 바이트 → {@code 300KB} · {@code 2MB} · {@code 1.4MB}(user-web {@code UtilEmoticonSpec.bytes} 와 같은 글자) */
    public static String bytes(long bytes) {
        if (bytes >= 1024 * 1024) {
            double mb = Math.round(bytes / (1024.0 * 1024.0) * 10) / 10.0;
            return (mb == Math.floor(mb) ? String.valueOf((long) mb) : String.format(Locale.ROOT, "%.1f", mb)) + "MB";
        }
        return Math.max(1, Math.round(bytes / 1024.0)) + "KB";
    }

    /** 화면과 같은 글자 수(UTF-16 길이 — 한글·영문은 한 글자) */
    public static int length(String text) {
        return text == null ? 0 : text.length();
    }
}
