package com.community.trial.entity;

import java.util.Optional;

/** 제공형(받아서 가짐) · 대여형(반납) — V241 {@code trials.form} — docs/features/trial-group-contract.md */
public enum TrialForm {
    GIVE,
    LEND;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialForm> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialForm x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
