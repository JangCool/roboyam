package com.community.mfa.dto;

import java.time.Instant;

/**
 * 비밀번호가 맞은 뒤의 판정({@code MfaPolicy#decide}) — 계약 「상태 판정」 표.
 *
 * @param method      바로 로그인할 때의 방식(OFF · TRUSTED_DEVICE · GRACE). 2차 대기면 null
 * @param stage       화면에 알릴 단계(VERIFY · ENROLL · GRACE). 바로 로그인이고 안내도 없으면 null
 * @param graceEndsAt GRACE 의 유예 마감. 그 밖은 null
 */
public record MfaDecision(MfaLoginMethod method, MfaStage stage, Instant graceEndsAt) {

    public static MfaDecision login(MfaLoginMethod method) {
        return new MfaDecision(method, null, null);
    }

    public static MfaDecision grace(Instant graceEndsAt) {
        return new MfaDecision(MfaLoginMethod.GRACE, MfaStage.GRACE, graceEndsAt);
    }

    public static MfaDecision pending(MfaStage stage) {
        return new MfaDecision(null, stage, null);
    }

    /** 2차 대기(세션을 만들지 않는다)인가 */
    public boolean isPending() {
        return method == null;
    }
}
