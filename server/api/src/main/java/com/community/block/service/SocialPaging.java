package com.community.block.service;

import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.regex.Pattern;

/**
 * 개인 목록(차단한 회원·메모·쪽지함·대화방)의 쪽 크기와 커서 — 크기 1~50(빠지면 기본값), 커서는 앞 응답의 {@code nextCursor}(Snowflake 십진 글자).
 * 틀리면 400 {@code VALIDATION} {@code fields.size}·{@code fields.cursor}. 회원 ID 글자도 여기서 읽는다(틀리면 null — 부르는 쪽이 404 로 바꾼다).
 */
public final class SocialPaging {

    public static final int MAX_SIZE = 50;
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");

    private SocialPaging() {
    }

    public static int size(Integer size, int fallback) {
        if (size == null) {
            return fallback;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw ValidationException.of("size", new MessageRef("personal.size.range", String.valueOf(MAX_SIZE)));
        }
        return size;
    }

    /** 커서 — 없으면 null(첫 쪽) */
    public static Long cursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        Long id = id(cursor.strip());
        if (id == null) {
            throw ValidationException.of("cursor", new MessageRef("personal.cursor.invalid"));
        }
        return id;
    }

    /** Snowflake 십진 글자 → 값. 형식이 틀리면 null */
    public static Long id(String text) {
        if (text == null || !DIGITS.matcher(text).matches()) {
            return null;
        }
        try {
            long value = Long.parseLong(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
