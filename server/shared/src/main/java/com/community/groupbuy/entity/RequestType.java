package com.community.groupbuy.entity;

/** 요청 종류({@code group_buy_requests.type}) — 발송 전 취소 요청(운영자 승인) · 배송완료 뒤 반품(commerce.md 10-3·10-8) */
public enum RequestType {
    CANCEL, RETURN
}
