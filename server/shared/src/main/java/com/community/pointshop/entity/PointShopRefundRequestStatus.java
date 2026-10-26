package com.community.pointshop.entity;

/** 환불 신청 상태 — 대기 · 수락(환불함) · 거절(사유). 결정은 한 번뿐(V87 트리거) */
public enum PointShopRefundRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
