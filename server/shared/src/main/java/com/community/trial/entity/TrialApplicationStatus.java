package com.community.trial.entity;

import java.util.Optional;

/** 체험단 신청 상태(V241 {@code trial_applications.status}) — docs/features/trial-group-contract.md */
public enum TrialApplicationStatus {
    APPLIED,
    SELECTED,
    NOT_SELECTED,
    CANCELLED;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialApplicationStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialApplicationStatus x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
