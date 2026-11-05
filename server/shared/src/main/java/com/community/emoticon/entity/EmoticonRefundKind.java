package com.community.emoticon.entity;

/** 환불 종류(V142 {@code refund_kind}, admin-web {@code EmoticonRefundKind}) — 회원 셀프(산 뒤 환불 기간 안 · 한 번도 안 씀) · 운영자 예외(사유 · 권한 {@code emoticon.sales}) */
public enum EmoticonRefundKind {
    SELF,
    EXCEPTION
}
