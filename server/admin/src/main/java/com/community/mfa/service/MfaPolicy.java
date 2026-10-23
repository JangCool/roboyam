package com.community.mfa.service;

import com.community.mfa.dto.MfaDecision;
import com.community.mfa.dto.MfaLoginMethod;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.MfaStage;
import java.time.Duration;
import java.time.Instant;

/**
 * 관리자 2차 인증의 판정 규칙 — 상태가 없는 순수 함수(docs/features/commerce-foundation-contract.md 「0-3」 상태 판정, 결정 8, 화면 확정 결정 1).
 *
 * <ul>
 *   <li><b>유예 마감</b> = max(켠 시각, 계정을 만든 시각) + 지금 유예 일수 — 저장하지 않고 매번 계산한다(켜진 채로 일수를 바꾸면 바로 반영된다). 꺼져 있으면 없다.
 *       마감 시각 <b>그 순간부터</b> 유예가 지난 것으로 본다(마감 전까지만 유예 안).</li>
 *   <li><b>상태 판정</b>(비밀번호가 맞은 뒤): 꺼짐 → 바로 로그인(OFF) · 켜짐+등록됨+유효한 신뢰 기기 → 바로 로그인(TRUSTED_DEVICE) · 켜짐+등록됨 → VERIFY 대기 ·
 *       켜짐+미등록+유예 안 → 로그인 + GRACE 안내 · 켜짐+미등록+유예 지남 → ENROLL 대기.</li>
 * </ul>
 */
public final class MfaPolicy {

    private MfaPolicy() {
    }

    /** 유예 마감. 꺼져 있거나 켠 시각을 모르면 null */
    public static Instant graceEndsAt(MfaSettings settings, Instant operatorCreatedAt) {
        if (!settings.enabled() || settings.enabledAt() == null) {
            return null;
        }
        Instant start = operatorCreatedAt != null && operatorCreatedAt.isAfter(settings.enabledAt()) ? operatorCreatedAt : settings.enabledAt();
        return start.plus(Duration.ofDays(settings.graceDays()));
    }

    /** 켜져 있고 미등록이며 유예 마감이 지났는가(마감 시각 포함) */
    public static boolean graceExpired(MfaSettings settings, Instant operatorCreatedAt, Instant now) {
        Instant end = graceEndsAt(settings, operatorCreatedAt);
        return end != null && !now.isBefore(end);
    }

    /**
     * 비밀번호가 맞은 뒤의 판정.
     *
     * @param enrolled      OTP 를 등록했는가
     * @param trustedDevice 이 운영자의 기간이 남은 신뢰 기기 쿠키를 냈는가
     */
    public static MfaDecision decide(MfaSettings settings, boolean enrolled, boolean trustedDevice, Instant operatorCreatedAt, Instant now) {
        if (!settings.enabled()) {
            return MfaDecision.login(MfaLoginMethod.OFF);
        }
        if (enrolled) {
            return trustedDevice ? MfaDecision.login(MfaLoginMethod.TRUSTED_DEVICE) : MfaDecision.pending(MfaStage.VERIFY);
        }
        Instant end = graceEndsAt(settings, operatorCreatedAt);
        if (end != null && now.isBefore(end)) {
            return MfaDecision.grace(end);
        }
        return MfaDecision.pending(MfaStage.ENROLL);
    }
}
