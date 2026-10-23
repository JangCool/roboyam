package com.community.mail.outbox.dto;

/**
 * 대기열 메일 한 통을 보내 본 결과.
 *
 * <ul>
 *   <li>{@link Outcome#SENT} 보냄 — {@code toMasked} 는 가린 받는 주소.</li>
 *   <li>{@link Outcome#CANCELED} 보내지 않기로 함 — 받을 수 없는 회원(없음·탈퇴·주소 없음·미인증)이거나 종류를 모름. {@code reason} 은 짧은 코드.</li>
 *   <li>{@link Outcome#RETRY} 지금은 못 보냄 — 메일 서버가 설정돼 있지 않다 등. 다음 시도로 미룬다. (보내다 던진 예외도 재시도다)</li>
 * </ul>
 */
public record OutboxDelivery(Outcome outcome, String toMasked, String reason) {

    public enum Outcome { SENT, CANCELED, RETRY }

    public static OutboxDelivery sent(String toMasked) {
        return new OutboxDelivery(Outcome.SENT, toMasked, null);
    }

    public static OutboxDelivery canceled(String reason) {
        return new OutboxDelivery(Outcome.CANCELED, null, reason);
    }

    public static OutboxDelivery retry(String reason) {
        return new OutboxDelivery(Outcome.RETRY, null, reason);
    }
}
