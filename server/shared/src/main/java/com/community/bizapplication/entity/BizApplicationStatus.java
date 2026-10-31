package com.community.bizapplication.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 신청 건의 저장 상태 8종(★-3). 화면의 예약·진행·종료는 {@link #SCHEDULED} 안에서 시각으로 계산한다({@code BizPhase}). 전이 표는 {@code BizTransitions}.
 */
public enum BizApplicationStatus {
    SUBMITTED, REJECTED, CANCELED, AWAITING_PAYMENT, EXPIRED, AWAITING_DEPOSIT, SCHEDULED, TAKEN_DOWN;

    /** 자리(한도)를 차지하는 상태 — 승인 순간부터 결제·입금 대기도 차지한다(★-2). 내림도 되돌릴 수 있어 차지한다(가정) */
    public static final Set<BizApplicationStatus> OCCUPYING = EnumSet.of(AWAITING_PAYMENT, AWAITING_DEPOSIT, SCHEDULED, TAKEN_DOWN);

    /** 승인 뒤 결제 전(결제·입금 대기) */
    public static final Set<BizApplicationStatus> AWAITING = EnumSet.of(AWAITING_PAYMENT, AWAITING_DEPOSIT);

    /** 끝난 상태 — 더 바뀌지 않는다 */
    public static final Set<BizApplicationStatus> CLOSED = EnumSet.of(REJECTED, CANCELED, EXPIRED);

    public boolean occupying() {
        return OCCUPYING.contains(this);
    }
}
