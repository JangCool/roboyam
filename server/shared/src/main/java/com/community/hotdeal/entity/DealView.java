package com.community.hotdeal.entity;

/** 읽을 때 계산한 딜 상태 — 우선순위 ENDED > EXPIRED > FLAGGED > ACTIVE(계약 「데이터 모양」, 관리자 상태 우선순위와 같다) */
public enum DealView {
    ACTIVE,
    FLAGGED,
    ENDED,
    EXPIRED;

    /** 진행 중(공개 집계 — 같은 딜·가젯 등에 드는 상태)인가: ACTIVE · FLAGGED */
    public boolean live() {
        return this == ACTIVE || this == FLAGGED;
    }

    /** 목록 차례 — 진행 중 0 → 의심 1 → 끝남(종료·기간 종료) 2 */
    public int rank() {
        return switch (this) {
            case ACTIVE -> 0;
            case FLAGGED -> 1;
            case ENDED, EXPIRED -> 2;
        };
    }
}
