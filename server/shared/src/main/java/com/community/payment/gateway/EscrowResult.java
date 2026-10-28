package com.community.payment.gateway;

/**
 * 에스크로 호출 결과(공구 계약 결정 64). 포트원의 「이미 등록·완료」 응답은 {@link Outcome#DONE} 으로 본다. 연결 실패·5xx 는 예외(503 — 부르는 쪽 재시도 대기열).
 *
 * @param errorType 거절의 포트원 오류 {@code type}(가린 오류 한 줄용)
 */
public record EscrowResult(Outcome outcome, String errorType) {

    public enum Outcome {
        /** 등록·수정·구매 확정을 마쳤다(이미 마친 것 포함) */
        DONE,
        /** 에스크로 결제가 아니다 — 부를 일이 없다 */
        NOT_ESCROW,
        /** 포트원이 거절했다(4xx) — 재시도 */
        FAILED
    }

    public static EscrowResult done() {
        return new EscrowResult(Outcome.DONE, null);
    }

    public static EscrowResult notEscrow() {
        return new EscrowResult(Outcome.NOT_ESCROW, null);
    }

    public static EscrowResult failed(String errorType) {
        return new EscrowResult(Outcome.FAILED, errorType);
    }
}
