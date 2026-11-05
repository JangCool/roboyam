package com.community.addressbook;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.addressbook.service.AddressFieldRules;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 배송지 칸 규칙(docs/features/address-book-contract.md 「필드·검증」) — 주소록과 공구 주문 배송지가 같이 쓰는 한 곳({@link AddressFieldRules}).
 * 받는 분 1~30 · 전화 숫자 9~11(하이픈 허용) · 우편번호 숫자 5 · 주소 1~100 · 상세 0~100 · 메모 0~50, 앞뒤 공백을 뺀 길이.
 */
class AddressFieldRulesTest {

    private static Map<String, ?> check(String recipient, String phone, String postcode, String address1, String address2, String memo) {
        return AddressFieldRules.violations("", recipient, phone, postcode, address1, address2, memo);
    }

    @Test
    void 경계값은_통과하고_빈_상세_주소_메모는_된다() {
        assertThat(check("가".repeat(30), "010-1234-5678", "06236", "가".repeat(100), "나".repeat(100), "다".repeat(50))).isEmpty();
        assertThat(check("홍", "021234567", "12345", "주", null, null)).isEmpty();
        assertThat(check("  홍길동  ", " 01012345678 ", " 06236 ", " 서울 ", "", "")).as("앞뒤 공백은 뺀다").isEmpty();
    }

    @Test
    void 틀린_칸을_모두_모은다_앞머리는_부르는_쪽이_정한다() {
        Map<String, ?> all = AddressFieldRules.violations("address.", "가".repeat(31), "010-12", "1234", "가".repeat(101), "나".repeat(101), "다".repeat(51));
        assertThat(all.keySet()).containsExactly("address.recipient", "address.phone", "address.postcode", "address.address1", "address.address2", "address.memo");
        assertThat(check(null, null, null, null, null, null).keySet()).containsExactly("recipient", "phone", "postcode", "address1");
        assertThat(check("   ", "", "", "  ", null, null).keySet()).containsExactly("recipient", "phone", "postcode", "address1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01234567", "010123456789", "010-1234-567a", "+82-10-1234-5678", "---------", "010 1234 5678"})
    void 전화는_숫자_9에서_11자리와_하이픈만(String phone) {
        assertThat(check("홍길동", phone, "06236", "서울", null, null)).containsOnlyKeys("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "123456", "1234a", "12 345"})
    void 우편번호는_숫자_5자리(String postcode) {
        assertThat(check("홍길동", "01012345678", postcode, "서울", null, null)).containsOnlyKeys("postcode");
    }
}
