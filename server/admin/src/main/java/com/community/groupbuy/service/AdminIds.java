package com.community.groupbuy.service;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.function.Supplier;

/** 관리자 공구 API 의 글자 ID — 주소의 ID 는 형식이 틀리면 404, 본문의 ID 는 400 {@code fields.<칸>} */
final class AdminIds {

    private AdminIds() {
    }

    static long parse(String text) {
        return parse(text, () -> new CommonException(ErrorCode.NOT_FOUND));
    }

    static long parse(String text, Supplier<? extends RuntimeException> notFound) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw notFound.get();
        }
    }

    static long field(String text, String field) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw ValidationException.of(field, new MessageRef("groupbuy.id.invalid"));
        }
    }

    static Long optional(String text, String field) {
        return text == null || text.isBlank() ? null : field(text, field);
    }

    static <E extends Enum<E>> E value(Class<E> type, String text, String field) {
        try {
            return Enum.valueOf(type, text == null ? "" : text);
        } catch (IllegalArgumentException e) {
            throw ValidationException.of(field, new MessageRef("groupbuy.value.invalid"));
        }
    }
}
