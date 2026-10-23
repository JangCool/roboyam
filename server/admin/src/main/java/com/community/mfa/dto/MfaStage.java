package com.community.mfa.dto;

/**
 * 비밀번호가 맞은 뒤의 2차 인증 단계(로그인 응답 {@code mfa.stage}). {@code VERIFY}·{@code ENROLL} 은 「2차 대기」 세션(로그인 아님), {@code GRACE} 는 이미 로그인된 상태의 안내다.
 * 이름은 화면과 주고받는 값이라 바꾸지 않는다.
 */
public enum MfaStage {
    /** 등록됨 — OTP(또는 복구 코드)를 물어본다 */
    VERIFY,
    /** 미등록·유예 지남 — 등록해야 로그인된다 */
    ENROLL,
    /** 미등록·유예 안 — 로그인됐고 등록을 안내만 한다 */
    GRACE
}
