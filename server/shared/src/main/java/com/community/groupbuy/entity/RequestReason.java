package com.community.groupbuy.entity;

/**
 * 요청 줄 사유({@code group_buy_request_items.reason}, 계약 결정 44·54) — 하자·오배송·배송 중 훼손은 업체 부담(반품비 없음·교환 가능), 단순 변심·기타는 회원 부담(10-8 표).
 */
public enum RequestReason {
    CHANGE_OF_MIND, DEFECT, WRONG_ITEM, DAMAGED, OTHER;

    /** 업체 부담 사유인가 */
    public boolean sellerFault() {
        return this == DEFECT || this == WRONG_ITEM || this == DAMAGED;
    }
}
