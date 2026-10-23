package com.community.mfa.dto;

import java.time.Instant;

/**
 * 로그인 보안 설정({@code admin_security_settings} 한 행).
 *
 * @param enabled     2차 인증 사용
 * @param graceDays   미등록 운영자의 유예 일수(1~90)
 * @param trustMonths 기기 신뢰 개월(1~12) — 일수는 {@link #trustDays()}
 * @param maxFailures OTP 실패 한도(3~10) — 닿으면 관리자 로그인 잠금 시간만큼 잠근다
 * @param enabledAt   마지막으로 켠 시각(유예의 시작). 켠 적 없으면 null
 * @param enabledBy   마지막으로 켠 운영자. 없으면 null
 */
public record MfaSettings(boolean enabled, int graceDays, int trustMonths, int maxFailures, Instant enabledAt, Long enabledBy) {

    /** 한 달 = 30일로 센다(계약 「기본 1개월 = 30일」) */
    public static final int DAYS_PER_MONTH = 30;

    public int trustDays() {
        return trustMonths * DAYS_PER_MONTH;
    }
}
