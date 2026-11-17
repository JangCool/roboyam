package com.community.trial.entity;

import java.util.Optional;

/** 협찬 숨김 신고 확인 상태 — 대기 · 확인(협찬으로 바꿈) · 무시 — docs/features/trial-group-contract.md */
public enum SponsorReviewStatus {
    PENDING,
    CONFIRMED,
    DISMISSED;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<SponsorReviewStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (SponsorReviewStatus x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
