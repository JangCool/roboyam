package com.community.trial.entity;

import java.util.Optional;

/** 체험단 모집 상태(V241 {@code trials.status}) — docs/features/trial-group-contract.md */
public enum TrialStatus {
    DRAFT,
    RECRUITING,
    SELECTING,
    RUNNING,
    CLOSED,
    CANCELLED;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialStatus x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
