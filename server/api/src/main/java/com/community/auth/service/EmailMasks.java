package com.community.auth.service;

/** 화면·메일에 보일 가린 이메일 — {@code m***@example.com}(앞 한 글자와 도메인만). 원문을 내보내지 않는 자리(변경 대기 이메일·변경 알림 메일)가 쓴다 */
public final class EmailMasks {

    private EmailMasks() {
    }

    /** 가린 주소. 비었거나 형식이 아니면 null */
    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return null;
        }
        int firstEnd = email.offsetByCodePoints(0, 1);
        return email.substring(0, Math.min(firstEnd, at)) + "***" + email.substring(at);
    }
}
