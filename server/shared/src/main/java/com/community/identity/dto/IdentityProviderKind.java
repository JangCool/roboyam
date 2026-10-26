package com.community.identity.dto;

/** 본인인증 구현 — 표 {@code identity_verifications.provider} · {@code identity_verification_attempts.provider} 의 CHECK 와 같은 값. 화면에는 이름 그대로(PORTONE · FAKE) 나간다 */
public enum IdentityProviderKind {
    /** 포트원 통합 본인인증(휴대폰) */
    PORTONE,
    /** 가짜 구현 — local·시험 전용(dev·prod 에서는 기동을 멈춘다) */
    FAKE
}
