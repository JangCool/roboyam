package com.community.batch.entity;

/** 실행 요청의 상태({@code batch_run_requests.status}) — {@code PENDING} → {@code RUNNING} → {@code DONE} 또는 {@code REJECTED} */
public enum BatchRequestStatus {
    /** 대기 — 배치를 켠 서버가 아직 안 집었다 */
    PENDING,
    /** 집음 — 실행 중(실행이 시작되면 run_id 가 생긴다) */
    RUNNING,
    /** 끝남 — 결과는 그 실행 로그 */
    DONE,
    /** 거절 — 집을 때 이미 실행 중 등(사유는 message_key) */
    REJECTED;

    /** 끝나지 않은 요청인가(같은 배치에 하나만 있을 수 있다) */
    public boolean open() {
        return this == PENDING || this == RUNNING;
    }
}
