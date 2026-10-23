package com.community.mfa.dto.response;

/**
 * 등록 시작 — 비밀키(base32, 대문자)와 QR 에 넣을 otpauth 주소. 응답에만 실리고 로그·감사에는 없다({@link #toString()} 감춤).
 *
 * @param issuer      사이트 이름
 * @param accountName 운영자 이메일
 */
public record MfaEnrollmentView(String secret, String otpauthUri, String issuer, String accountName) {

    @Override
    public String toString() {
        return "MfaEnrollmentView[secret=(감춤), issuer=" + issuer + "]";
    }
}
