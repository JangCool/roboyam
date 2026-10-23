package com.community.batch.entity;

/** 실행 요청의 종류({@code batch_run_requests.kind}) */
public enum BatchRequestKind {
    /** 지금 실행(파라미터 지정 실행 포함) */
    NOW,
    /** 실패 지점부터 다시 */
    RESUME,
    /** 실패 건만 다시 */
    RETRY_FAILED;

    /** 이 요청이 만드는 실행의 방식 */
    public BatchRunMode runMode() {
        return switch (this) {
            case NOW -> BatchRunMode.NORMAL;
            case RESUME -> BatchRunMode.RESUME;
            case RETRY_FAILED -> BatchRunMode.RETRY_FAILED;
        };
    }
}
