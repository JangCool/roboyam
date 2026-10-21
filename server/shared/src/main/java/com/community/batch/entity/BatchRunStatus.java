package com.community.batch.entity;

/** 배치 실행의 상태. 화면에 나가는 값 이름은 {@code RUNNING}·{@code OK}·{@code FAIL}·{@code SKIP} 이다 */
public enum BatchRunStatus {
    /** 실행 중 */
    RUNNING,
    /** 성공 */
    OK,
    /** 실패 */
    FAIL,
    /** 건너뜀 — 돌았지만 처리할 것이 없었다(처리 건수 0) */
    SKIP
}
