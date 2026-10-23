package com.community.batch.entity;

/** 한 실행이 어떻게 시작됐나({@code batch_runs.run_mode}). 화면에 나가는 값 이름 그대로다 */
public enum BatchRunMode {
    /** 보통 — 예약 실행·지금 실행(파라미터 지정 실행 포함) */
    NORMAL,
    /** 실패 지점부터 다시 — 원래 실행의 파라미터로, 끝난 단계는 건너뛰고 실패한 단계는 체크포인트 다음부터 */
    RESUME,
    /** 실패 건만 다시 — 원래 실행의 미해결 실패 건만 한 건씩 */
    RETRY_FAILED
}
