package com.community.groupbuy.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 요청 상태({@code group_buy_requests.status}, 계약 결정 44·68·69). 이름은 DB 값이다.
 * AWAITING_FEE(반품비 부족분 결제 전 — 접수 전) → REQUESTED → APPROVED(회수 안내) → RECEIVED(돌려받음) → COMPLETED / REJECTED · WITHDRAWN · SUPERSEDED · EXPIRED.
 */
public enum RequestStatus {
    AWAITING_FEE, REQUESTED, APPROVED, RECEIVED, COMPLETED, REJECTED, WITHDRAWN, SUPERSEDED, EXPIRED;

    /** 열린 요청 — 주문당 하나(부분 유일 색인), 열린 요청이 있으면 발송·운영 환불을 막는다(결정 47·71) */
    public static final Set<RequestStatus> OPEN = EnumSet.of(AWAITING_FEE, REQUESTED, APPROVED, RECEIVED);

    public static String sqlOpen() {
        return "'AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED'";
    }
}
