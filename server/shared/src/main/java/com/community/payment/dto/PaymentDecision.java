package com.community.payment.dto;

/**
 * 도메인 처리기의 결제 판정 — 받아들임 또는 거절(사유). 거절이면 결제 모듈이 {@code DOMAIN_REJECTED} 로 표시하고 전액 자동 취소를 넣는다.
 *
 * @param accepted 받아들였는가
 * @param reason   거절 사유 코드(대문자·숫자·밑줄, 예 {@code GROUP_BUY_CLOSED}) — 이력에 남는다. 개인정보를 넣지 않는다. 받아들이면 null
 */
public record PaymentDecision(boolean accepted, String reason) {

    private static final PaymentDecision ACCEPT = new PaymentDecision(true, null);

    public PaymentDecision {
        if (!accepted && (reason == null || !reason.matches("[A-Z][A-Z0-9_]{0,59}"))) {
            throw new IllegalArgumentException("거절 사유 코드는 대문자·숫자·밑줄 60자다");
        }
        if (accepted && reason != null) {
            throw new IllegalArgumentException("받아들인 판정에는 사유가 없다");
        }
    }

    public static PaymentDecision accept() {
        return ACCEPT;
    }

    public static PaymentDecision reject(String reason) {
        return new PaymentDecision(false, reason);
    }
}
