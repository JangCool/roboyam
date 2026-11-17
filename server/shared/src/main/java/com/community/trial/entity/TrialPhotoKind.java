package com.community.trial.entity;

import java.util.Optional;

/** 체험단 사진 종류 — 입고 · 발송 전 · 반납 · 파손 — docs/features/trial-group-contract.md */
public enum TrialPhotoKind {
    STOCK,
    PRE_SHIP,
    RETURN,
    DAMAGE;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialPhotoKind> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialPhotoKind x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
