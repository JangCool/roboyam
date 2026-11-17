package com.community.trial.entity;

import java.util.Optional;

/** 파손 판정 — 정상 마모 · 택배 파손 · 회원 과실(90일 금지) · 고의·숨김 반납(영구 금지 + 리뷰어 회수) — docs/features/trial-group-contract.md */
public enum TrialDamageVerdict {
    NORMAL_WEAR,
    COURIER,
    MEMBER_FAULT,
    INTENTIONAL;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialDamageVerdict> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialDamageVerdict x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
