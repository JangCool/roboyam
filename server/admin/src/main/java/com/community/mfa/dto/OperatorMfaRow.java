package com.community.mfa.dto;

import java.time.Instant;

/**
 * {@code operator_mfa} 한 행. 비밀키는 암호문(「v1:」)으로만 들고 다닌다 — {@link #toString()} 에 싣지 않는다.
 *
 * @param secretEnc        확정된 비밀키 암호문(없으면 미등록)
 * @param lastStep         마지막으로 받아들인 TOTP 칸(없으면 null)
 * @param pendingSecretEnc 등록 중(확정 전) 비밀키 암호문
 * @param pendingReenroll  등록 중인 것이 「다시 등록」인가
 */
public record OperatorMfaRow(long operatorId, String secretEnc, Instant enrolledAt, Long lastStep, Instant lastUsedAt, String pendingSecretEnc,
                             Instant pendingStartedAt, boolean pendingReenroll) {

    public boolean enrolled() {
        return secretEnc != null;
    }

    @Override
    public String toString() {
        return "OperatorMfaRow[operatorId=" + operatorId + ", enrolled=" + enrolled() + ", pending=" + (pendingSecretEnc != null) + "]";
    }
}
