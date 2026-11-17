package com.community.trial.entity;

import java.util.Optional;

/** 보내는 쪽 — 광고주(회원 동의로 이름·연락처·주소 제공) · 운영자가 대신(주소는 광고주에게 가지 않는다) — docs/features/trial-group-contract.md */
public enum TrialShipper {
    ADVERTISER,
    OPERATOR;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialShipper> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialShipper x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
