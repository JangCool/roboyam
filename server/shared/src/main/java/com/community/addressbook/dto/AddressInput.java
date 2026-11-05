package com.community.addressbook.dto;

/**
 * 주소록에 넣거나 고칠 배송지(평문 — 서비스가 검사·암호화한다). {@code label} 은 비우면 null 로 저장한다. {@code toString} 에 값을 싣지 않는다.
 *
 * @param makeDefault 이 배송지를 기본으로(첫 배송지는 이 값과 상관없이 기본 — 기본을 끄는 데는 쓰지 않는다)
 */
public record AddressInput(String label, String recipient, String phone, String postcode, String address1, String address2, String memo, boolean makeDefault) {

    @Override
    public String toString() {
        return "AddressInput[가림, makeDefault=" + makeDefault + "]";
    }
}
