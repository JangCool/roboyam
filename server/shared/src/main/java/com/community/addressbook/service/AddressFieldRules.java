package com.community.addressbook.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 배송지 칸 규칙 — 회원 주소록과 공구 주문 배송지(주문서·내 주문의 배송지 수정)가 <b>이 한 곳</b>을 쓴다(docs/features/address-book-contract.md 「필드·검증」).
 * 받는 분 1~30자 · 전화 숫자 9~11자리(하이픈 허용) · 우편번호 숫자 5자리 · 주소 1~100자 · 상세 주소 0~100자 · 메모 0~50자. 앞뒤 공백을 뺀 길이로 잰다.
 * 틀린 칸을 모두 모아 돌려준다(칸 이름 앞에 {@code prefix} — 주문은 {@code address.}, 주소록은 빈 글자). 값은 문구에 싣지 않는다.
 */
public final class AddressFieldRules {

    public static final int RECIPIENT_MAX = 30;
    public static final int PHONE_DIGITS_MIN = 9;
    public static final int PHONE_DIGITS_MAX = 11;
    public static final int ADDRESS_MAX = 100;
    public static final int MEMO_MAX = 50;

    private static final Pattern PHONE = Pattern.compile("[0-9-]{" + PHONE_DIGITS_MIN + ",15}");
    private static final Pattern POSTCODE = Pattern.compile("[0-9]{5}");

    private AddressFieldRules() {
    }

    /** 칸 이름(앞머리 포함) → 문구. 틀린 칸이 없으면 빈 맵 */
    public static Map<String, MessageRef> violations(String prefix, String recipient, String phone, String postcode, String address1, String address2, String memo) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (blankOrLonger(recipient, RECIPIENT_MAX)) {
            fields.put(prefix + "recipient", new MessageRef("address.field.recipient", RECIPIENT_MAX));
        }
        if (!validPhone(phone)) {
            fields.put(prefix + "phone", new MessageRef("address.field.phone", PHONE_DIGITS_MIN, PHONE_DIGITS_MAX));
        }
        if (postcode == null || !POSTCODE.matcher(postcode.strip()).matches()) {
            fields.put(prefix + "postcode", new MessageRef("address.field.postcode"));
        }
        if (blankOrLonger(address1, ADDRESS_MAX)) {
            fields.put(prefix + "address1", new MessageRef("address.field.address1", ADDRESS_MAX));
        }
        if (longer(address2, ADDRESS_MAX)) {
            fields.put(prefix + "address2", new MessageRef("address.field.address2", ADDRESS_MAX));
        }
        if (longer(memo, MEMO_MAX)) {
            fields.put(prefix + "memo", new MessageRef("address.field.memo", MEMO_MAX));
        }
        return fields;
    }

    static boolean validPhone(String phone) {
        if (phone == null || !PHONE.matcher(phone.strip()).matches()) {
            return false;
        }
        long digits = phone.chars().filter(Character::isDigit).count();
        return digits >= PHONE_DIGITS_MIN && digits <= PHONE_DIGITS_MAX;
    }

    private static boolean blankOrLonger(String value, int max) {
        return value == null || value.isBlank() || value.strip().length() > max;
    }

    private static boolean longer(String value, int max) {
        return value != null && value.strip().length() > max;
    }
}
