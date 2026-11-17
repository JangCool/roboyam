package com.community.trial.entity;

import java.util.Optional;

/** 체험단 처리한 쪽 — 운영자(관리자 앱) · 광고주(사업자 센터 회원) — docs/features/trial-group-contract.md */
public enum TrialActorType {
    OPERATOR,
    ADVERTISER;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialActorType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialActorType x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
