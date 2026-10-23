package com.community.mfa.dto;

/**
 * 로그인이 완성된 방식 — 감사 {@code LOGIN_SUCCESS} 의 {@code detail.mfa}(로그인 이력 화면이 읽는다). 이름은 기록에 남는 값이라 바꾸지 않는다.
 */
public enum MfaLoginMethod {
    /** 사이트에서 2차 인증이 꺼져 있다 */
    OFF,
    /** 유효한 신뢰 기기 쿠키로 OTP 를 묻지 않았다 */
    TRUSTED_DEVICE,
    /** OTP 로 확인했다 */
    TOTP,
    /** 복구 코드로 확인했다 */
    RECOVERY_CODE,
    /** 강제 등록을 마쳤다 */
    ENROLLED,
    /** 미등록이지만 유예 기간 안이다 */
    GRACE
}
