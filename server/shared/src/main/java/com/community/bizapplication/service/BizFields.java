package com.community.bizapplication.service;

import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/** 신청 건 입력 검사 도우미 — 칸 오류를 모아(선언 차례) 400 {@code VALIDATION} 으로 던진다. 문구 키는 {@code bizapp.*} */
public final class BizFields {

    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z.\\-]{1,20}");

    private final Map<String, MessageRef> errors = new LinkedHashMap<>();

    public static BizFields start() {
        return new BizFields();
    }

    public void add(String field, String key, Object... args) {
        errors.putIfAbsent(field, new MessageRef(key, args));
    }

    public boolean has(String field) {
        return errors.containsKey(field);
    }

    public void throwIfAny() {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** 글자(앞뒤 공백 뺌) 1~max — 비었거나 길면 오류, 값은 그대로 돌려준다 */
    public String text(String field, String value, int max) {
        String s = strip(value);
        if (s == null) {
            add(field, "bizapp.field.required");
        } else if (s.length() > max) {
            add(field, "bizapp.field.tooLong", max);
        }
        return s;
    }

    /** 고를 수 있는 값 — 모르면 오류 */
    public <T> T oneOf(String field, String value, Function<String, T> parse) {
        String s = strip(value);
        T out = null;
        if (s != null) {
            try {
                out = parse.apply(s);
            } catch (RuntimeException e) {
                out = null;
            }
        }
        if (out == null) {
            add(field, "bizapp.value.invalid");
        }
        return out;
    }

    public LocalDate date(String field, String value) {
        String s = strip(value);
        if (s == null) {
            add(field, "bizapp.field.required");
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            add(field, "bizapp.period.invalid");
            return null;
        }
    }

    public Instant instant(String field, String value) {
        String s = strip(value);
        if (s == null) {
            add(field, "bizapp.field.required");
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            add(field, "bizapp.value.invalid");
            return null;
        }
    }

    /** 정책 동의 버전 둘(H4) — 비면 {@code fields.agree} */
    public void agree(String adPolicyVersion, String refundPolicyVersion) {
        if (!version(adPolicyVersion) || !version(refundPolicyVersion)) {
            add("agree", "bizapp.agree.required");
        }
    }

    /** https(배너) 또는 http(s) 주소 — 500자 */
    public String link(String field, String value, boolean httpsOnly) {
        String s = strip(value);
        if (s == null) {
            add(field, "bizapp.field.required");
            return null;
        }
        if (s.length() > 500 || BizDealLinks.host(s).isEmpty() || (httpsOnly && !s.toLowerCase(java.util.Locale.ROOT).startsWith("https://"))) {
            add(field, httpsOnly ? "bizapp.link.https" : "bizapp.link.invalid");
        }
        return s;
    }

    public static boolean version(String value) {
        return value != null && VERSION.matcher(value.strip()).matches();
    }

    public static String strip(String value) {
        if (value == null) {
            return null;
        }
        String s = value.strip();
        return s.isEmpty() ? null : s;
    }

    /** ID 글자 → long(틀리면 null) */
    public static Long id(String value) {
        try {
            return value == null ? null : Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
