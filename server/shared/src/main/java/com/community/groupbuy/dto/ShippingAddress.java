package com.community.groupbuy.dto;

/**
 * 배송지 — 저장은 암호문(우편번호만 평문, commerce.md 10-9). {@code toString} 에 값을 싣지 않는다.
 */
public record ShippingAddress(String recipient, String phone, String postcode, String address1, String address2, String memo) {

    @Override
    public String toString() {
        return "ShippingAddress[가림]";
    }
}
