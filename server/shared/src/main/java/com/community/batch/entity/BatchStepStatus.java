package com.community.batch.entity;

/** 단계의 상태({@code batch_run_steps.status}) */
public enum BatchStepStatus {
    /** 아직 안 돎(실패로 멈춘 실행에서는 끝내 닿지 못한 단계) */
    PENDING,
    /** 실행 중 */
    RUNNING,
    /** 끝남 */
    OK,
    /** 실패 — 실행이 여기서 멈췄다 */
    FAIL,
    /** 건너뜀 — 고르지 않았거나, 이어 받은 실행에서 원래 실행이 이미 끝낸 단계거나, 할 일이 없었다 */
    SKIP
}
