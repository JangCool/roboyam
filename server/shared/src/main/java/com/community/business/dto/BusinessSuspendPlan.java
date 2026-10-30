package com.community.business.dto;

/**
 * 정지 때 걸린 신청 건 처리(H2) — 결제·입금 대기 · 시작 전 예약 · 진행 중. 기본값은 자동 취소 · 전액 환불 · 긴급 내림 + 일할 환불 심사.
 * 값은 신원 줄에 저장·감사하고, 실제 처리는 신청 건 백엔드의 {@code BusinessApplicationHook#onSuspended} 가 한다.
 */
public record BusinessSuspendPlan(Pending pending, Scheduled scheduled, Running running) {

    public enum Pending { CANCEL, KEEP }

    public enum Scheduled { REFUND_FULL, HOLD }

    public enum Running { TAKEDOWN_PRORATE, TAKEDOWN_ONLY }

    /** 정지 해제 때 정지로 내려간 진행 중 건 처리 — 남은 기간 연장 후 되돌리기 · 환불 유지 */
    public enum Restore { EXTEND_RESTORE, KEEP_REFUND }
}
