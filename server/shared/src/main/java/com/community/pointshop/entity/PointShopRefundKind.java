package com.community.pointshop.entity;

/** 환불 종류(환불한 구매만) — 회원 셀프 · 운영자 예외 환불(사유) · 정지 중 환불 신청을 운영자가 수락(V87) */
public enum PointShopRefundKind {
    SELF,
    EXCEPTION,
    REQUEST
}
