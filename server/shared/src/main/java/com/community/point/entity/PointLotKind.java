package com.community.point.entity;

/** 포인트 묶음의 종류(V85 {@code point_lots.kind}) — 무상은 소멸일이 반드시 있고 유상은 소멸이 없다(commerce.md D12) */
public enum PointLotKind {

    /** 무상(적립) — 받은 날부터 {@code point_settings.free_valid_days} 뒤 소멸 */
    FREE,
    /** 유상(충전) — 소멸 없음(3단계 충전부터 생긴다) */
    PAID
}
