package com.community.addressbook.dto;

import java.time.Instant;

/**
 * 주소록의 배송지 한 줄(복호화한 평문 — 회원 본인 화면에만 그대로 보인다, 계약 「권한·정책」). 빈 상세 주소·메모는 빈 글자. {@code toString} 에 값을 싣지 않는다.
 */
public record SavedAddress(long id, String label, String recipient, String phone, String postcode, String address1, String address2, String memo, boolean isDefault,
                           Instant updatedAt) {

    @Override
    public String toString() {
        return "SavedAddress[id=" + id + ", 가림]";
    }
}
