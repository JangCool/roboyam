package com.community.groupbuy.entity;

import java.util.EnumSet;
import java.util.Set;

/** 수량 이동 종류({@code group_buy_qty_movements.kind}) — 수량을 바꾸는 모든 길이 한 줄(계약 「마이그레이션 계획」) */
public enum QtyMovementKind {
    /** 선점(reserved +) */
    RESERVE,
    /** 선점 해제(reserved −) */
    RELEASE,
    /** 결제 확정(reserved − · current +) */
    CONFIRM,
    /** 결제된 수량 취소(current −) — 본인 취소·불발·중단 */
    CANCEL,
    /** 반품 기록(수량 불변) */
    RETURN,
    /** 다시 판매(current −) */
    RESTOCK,
    /** 추가 입고(max +) */
    ADD,
    /** 줄이기(max −) */
    REDUCE,
    /** 교환 출고(기록만) */
    EXCHANGE_OUT;

    /** 남은 수량을 늘리는 이동 — DEMAND 연장 판단·수량 풀림(결정 25·81) */
    public static final Set<QtyMovementKind> RELEASES_QTY = EnumSet.of(RELEASE, CANCEL, RESTOCK, ADD);
}
