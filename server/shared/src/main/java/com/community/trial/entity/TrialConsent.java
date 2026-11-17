package com.community.trial.entity;

import java.util.Optional;

/** 신청 동의 항목 — 광고주에게 이름·연락처·주소 제공(광고주가 보낼 때) · 미반납 확정 때 이름·연락처만 제공(운영자가 보낼 때·대여형) · 미반납 시 제품값 청구(대여형) · 과실·고의 파손 시 수리비 한도 청구(대여형) — docs/features/trial-group-contract.md */
public enum TrialConsent {
    PROVIDE_CONTACT,
    UNRETURNED_CONTACT,
    PRODUCT_CHARGE,
    DAMAGE_CHARGE;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialConsent> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialConsent x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
