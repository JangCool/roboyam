package com.community.osslicense.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 오픈소스 라이선스 고지의 칸 규칙(docs/features/oss-licenses-contract.md 「관리자」) — V180 의 표 제약과 같은 값이다.
 * 한 줄 칸은 앞뒤 공백을 떼고, 여러 줄 칸(안내 문구·저작권 고지·전문)은 줄바꿈을 {@code \n} 으로 맞추고 앞뒤 빈 줄만 뗀다. 비면 null.
 */
public final class OssNotices {

    public static final int INTRO_MAX = 1000;
    public static final int NAME_MAX = 100;
    public static final int VERSION_MAX = 50;
    public static final int LICENSE_MAX = 100;
    public static final int COPYRIGHT_MAX = 2000;
    public static final int LICENSE_TEXT_MAX = 100_000;
    public static final int SOURCE_URL_MAX = 500;

    /** 묶음 — 공개 화면 · 관리자 화면 · 서버 · 글꼴·아이콘 · 기타 */
    public static final List<String> CATEGORIES = List.of("USER", "ADMIN", "SERVER", "FONT_ICON", "OTHER");

    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private OssNotices() {
    }

    /** 한 줄 칸 — 앞뒤 공백을 떼고 비면 null */
    public static String line(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip();
        return v.isEmpty() ? null : v;
    }

    /** 여러 줄 칸 — 줄바꿈을 \n 으로 맞추고 줄 끝 공백·앞뒤 빈 줄을 뗀다. 비면 null */
    public static String multiline(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t]+\\n", "\n").strip();
        return v.isEmpty() ? null : v;
    }

    /** 고른 묶음(대문자) — 목록에 없으면 null */
    public static String category(String raw) {
        String v = line(raw);
        return v != null && CATEGORIES.contains(v) ? v : null;
    }

    /** 글자 수(DB char_length 와 같이 코드 포인트) */
    public static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** 길이 위반(키 {@code ossNotice.<칸>.tooLong}) */
    public static Optional<MessageRef> tooLong(String field, String value, int max) {
        return length(value) > max ? Optional.of(new MessageRef("ossNotice." + field + ".tooLong", String.valueOf(max))) : Optional.empty();
    }

    /** 원본 링크 위반 — http(s):// 로 시작하고 공백이 없어야 한다 */
    public static Optional<MessageRef> sourceUrlViolation(String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (length(value) > SOURCE_URL_MAX) {
            return Optional.of(new MessageRef("ossNotice.sourceUrl.tooLong", String.valueOf(SOURCE_URL_MAX)));
        }
        return HTTP_URL.matcher(value).matches() ? Optional.empty() : Optional.of(new MessageRef("ossNotice.sourceUrl.invalid"));
    }
}
