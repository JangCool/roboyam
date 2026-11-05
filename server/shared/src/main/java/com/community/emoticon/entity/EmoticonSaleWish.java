package com.community.emoticon.entity;

import java.util.Optional;

/** 작가의 판매 희망 — 무료 · 유료 판매 희망(가격은 운영자가 승인 때 정한다 — S2) */
public enum EmoticonSaleWish {
    FREE,
    PAID;

    public static Optional<EmoticonSaleWish> of(String text) {
        if ("FREE".equals(text)) {
            return Optional.of(FREE);
        }
        if ("PAID".equals(text)) {
            return Optional.of(PAID);
        }
        return Optional.empty();
    }
}
