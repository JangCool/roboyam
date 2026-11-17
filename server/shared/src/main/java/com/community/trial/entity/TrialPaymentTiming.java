package com.community.trial.entity;

import java.util.Optional;

/**
 * 체험단 광고비 결제 때(V244 — trial-group-contract 「유료 결제 연결」, 2026-11-17 사용자 결정 「선결제가 우선」) — 선결제(기본: 모집 인원으로 견적 → 결제가 끝나야 모집 공개, 차액 환불 없음)
 * · 후불(뽑기 뒤 뽑힌 인원으로 견적 → 결제가 끝나야 입고·발송).
 */
public enum TrialPaymentTiming {
    PREPAID,
    POSTPAID;

    /** 이름 → 값(앞뒤 공백·대소문자 무시, 모르면 빈 값) */
    public static Optional<TrialPaymentTiming> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(java.util.Locale.ROOT);
        for (TrialPaymentTiming x : values()) {
            if (x.name().equals(v)) {
                return Optional.of(x);
            }
        }
        return Optional.empty();
    }
}
