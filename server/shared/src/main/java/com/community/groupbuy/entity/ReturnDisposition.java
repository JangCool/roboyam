package com.community.groupbuy.entity;

/** 반품 상품 처리 방법(계약 결정 15) — 다시 판매(모집 중 + 단순 변심만) · 폐기 · 공급처 반송 · 보관 */
public enum ReturnDisposition {
    RESELL, DISCARD, RETURN_TO_SUPPLIER, KEEP
}
