package com.community.withdrawal.entity;

/**
 * 재가입·재인증 제한 해시의 종류 — 표 {@code identity_withdrawals.kind} 의 CHECK 와 같은 값(commerce.md 17-2, 세션 정리 결정 2).
 */
public enum WithdrawalKind {
    /** 이메일(소문자·공백 제거로 정규화한 뒤 해시) */
    EMAIL,
    /** 소셜 계정 식별값({@code <공급자>:<공급자의 회원 번호>}) */
    SOCIAL,
    /** 본인인증 DI — 해시는 {@code identity_verifications.di_hash} 와 같은 값(용도 {@code identity.di}) */
    IDENTITY_DI,
    /** 본인인증 CI — 해시는 {@code identity_verifications.ci_hash} 와 같은 값(용도 {@code identity.ci}) */
    IDENTITY_CI
}
