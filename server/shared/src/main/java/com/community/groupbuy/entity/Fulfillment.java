package com.community.groupbuy.entity;

/** 발송 시점(D1) — 즉시 발송(선착순만, 결제 확정 즉시 CONFIRMED) · 마감 후 발주 */
public enum Fulfillment {
    IMMEDIATE, AFTER_CLOSE
}
