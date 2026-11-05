package com.community.addressbook.dto.request;

import com.community.addressbook.dto.AddressInput;

/**
 * {@code POST /api/me/addresses} · {@code PUT /api/me/addresses/{id}} 본문(user-web {@code ShippingAddressRequest}). 칸 검사는 서비스가 모은다(400 {@code fields.<칸>}).
 * {@code toString} 에 값을 싣지 않는다.
 */
public record AddressBookRequest(String label, String recipient, String phone, String postcode, String address1, String address2, String memo, Boolean makeDefault) {

    public AddressInput toInput() {
        return new AddressInput(label, recipient, phone, postcode, address1, address2, memo, Boolean.TRUE.equals(makeDefault));
    }

    @Override
    public String toString() {
        return "AddressBookRequest[가림]";
    }
}
