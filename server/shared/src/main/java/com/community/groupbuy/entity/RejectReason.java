package com.community.groupbuy.entity;

/**
 * 요청 거절 사유 코드(계약 결정 55 — 전자상거래법 제17조 ②⑤⑥). 가치 감소 둘은 공구(상품)에 청약철회 제한 안내가 있어야 쓸 수 있다(⑥ — 표시하지 않으면 제한하지 못한다).
 * 이미 발송함은 발송 전 취소 요청만(10-3).
 */
public enum RejectReason {
    CUSTOMER_DAMAGE, USED_VALUE_LOSS, TIME_VALUE_LOSS, OUT_OF_WINDOW, ALREADY_SHIPPED;

    /** 청약철회 제한 안내가 있어야 쓰는 사유 */
    public boolean needsRestrictionNotice() {
        return this == USED_VALUE_LOSS || this == TIME_VALUE_LOSS;
    }
}
