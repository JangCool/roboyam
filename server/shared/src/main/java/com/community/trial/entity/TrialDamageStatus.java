package com.community.trial.entity;

import java.util.Optional;

/** 파손 신고 상태 — 이의 기다림 · 회원 이의 · 이의 기한 지나 운영자 확인 · 판정 — docs/features/trial-group-contract.md */
public enum TrialDamageStatus {
    REPORTED,
    OBJECTED,
    AWAITING,
    DECIDED;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialDamageStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialDamageStatus x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
