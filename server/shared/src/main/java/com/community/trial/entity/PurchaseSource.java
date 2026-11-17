package com.community.trial.entity;

import java.util.Optional;

/** 사용기 구입 경로(V241 {@code posts.purchase_source}) — 체험단 · 내 돈으로 샀어요 · 업체에서 받았어요(협찬) · 빌려서 써 봤어요 — docs/features/trial-group-contract.md */
public enum PurchaseSource {
    TRIAL,
    SELF,
    SPONSORED,
    BORROWED;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<PurchaseSource> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (PurchaseSource x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
