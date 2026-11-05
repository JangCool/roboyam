package com.community.addressbook.dto.response;

import com.community.addressbook.dto.SavedAddress;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * 주소록 응답(user-web {@code useAddressBookApi.types.ts}). 회원 본인 화면이라 값을 가리지 않는다(계약 「권한·정책」). ID 는 글자.
 */
public final class AddressBookView {

    private AddressBookView() {
    }

    /** 배송지 한 줄 — {@code label} 은 없으면 null, 상세 주소·메모는 없으면 빈 글자 */
    public record Item(String id, String label, String recipient, String phone, String postcode, String address1, String address2, String memo,
                       @JsonProperty("isDefault") boolean isDefault, Instant updatedAt) {

        public static Item of(SavedAddress a) {
            return new Item(Long.toString(a.id()), a.label(), a.recipient(), a.phone(), a.postcode(), a.address1(), a.address2(), a.memo(), a.isDefault(), a.updatedAt());
        }

        @Override
        public String toString() {
            return "AddressBookView.Item[id=" + id + ", 가림]";
        }
    }

    /** 목록 — 기본이 맨 위, 나머지는 최근 고친 순. {@code max} = 둘 수 있는 개수 */
    public record ListPage(List<Item> items, int max) {
    }
}
