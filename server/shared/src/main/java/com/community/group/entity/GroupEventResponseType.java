package com.community.group.entity;

import java.util.Arrays;
import java.util.Optional;

/** 일정에 대한 참석 응답 — 참석 · 불참 · 미정 */
public enum GroupEventResponseType {
    GOING,
    NOT_GOING,
    MAYBE;

    public static Optional<GroupEventResponseType> of(String raw) {
        String value = raw == null ? "" : raw.strip();
        return Arrays.stream(values()).filter(t -> t.name().equals(value)).findFirst();
    }
}
