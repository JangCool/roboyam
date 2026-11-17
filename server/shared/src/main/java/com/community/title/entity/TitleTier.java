package com.community.title.entity;

import java.util.Optional;

/** 칭호 단계(동·은·금) — 모양은 그대로, 색만 바뀐다(계약 「아이콘」) */
public enum TitleTier {
    BRONZE,
    SILVER,
    GOLD;

    public static Optional<TitleTier> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (TitleTier t : values()) {
            if (t.name().equals(value.strip())) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** 둘 중 높은 단계(null 은 가장 낮다) */
    public static TitleTier max(TitleTier a, TitleTier b) {
        if (a == null) {
            return b;
        }
        return b == null || a.ordinal() >= b.ordinal() ? a : b;
    }
}
