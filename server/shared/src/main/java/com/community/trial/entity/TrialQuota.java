package com.community.trial.entity;

import java.util.Optional;

/** 뽑기 몫 — 리뷰어 몫(신청 때 리뷰어 칭호가 있으면) · 일반 몫 — docs/features/trial-group-contract.md */
public enum TrialQuota {
    REVIEWER,
    GENERAL;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialQuota> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialQuota x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
