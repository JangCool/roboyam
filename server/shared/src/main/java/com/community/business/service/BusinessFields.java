package com.community.business.service;

import java.util.Locale;

/**
 * 사업자 신원 칸의 정규화·검사·가린 값(business-contract 「필드·검증」). 정규화한 값으로 암호화·해시하므로 같은 번호는 늘 같은 해시다.
 */
public final class BusinessFields {

    /** 등록번호 체크섬 가중치(국세청 공개 규칙) — 앞 9자리에 곱하고, 9번째 자리 × 5 의 십의 자리를 더한다 */
    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    private BusinessFields() {
    }

    /** 숫자만 남긴다(하이픈·공백 제거) — null 이면 null */
    public static String digits(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }

    /** 사업자등록번호 10자리 + 체크섬 */
    public static boolean validBizNo(String digits) {
        if (digits == null || !digits.matches("[0-9]{10}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        return (10 - sum % 10) % 10 == digits.charAt(9) - '0';
    }

    /** 끝 4자리(평문으로 남기는 값) */
    public static String last4(String digits) {
        return digits.substring(digits.length() - 4);
    }

    /** 가린 등록번호 — 끝 4자리만 보인다({@code ***-**-*6789}) */
    public static String maskBizNo(String last4) {
        return last4 == null ? null : "***-**-*" + last4;
    }

    /** 평문 등록번호 표시({@code 123-45-67890}) */
    public static String formatBizNo(String digits) {
        return digits == null || digits.length() != 10 ? digits : digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
    }

    /** 이름 — 첫 글자 · 가운데 * · 끝 글자(두 글자는 「홍*」) */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        int[] cps = name.codePoints().toArray();
        if (cps.length == 1) {
            return "*";
        }
        StringBuilder out = new StringBuilder().appendCodePoint(cps[0]);
        out.append("*".repeat(Math.max(1, cps.length - 2)));
        if (cps.length > 2) {
            out.appendCodePoint(cps[cps.length - 1]);
        }
        return out.toString();
    }

    /** 연락처 — {@code 010-****-5678} */
    public static String maskPhone(String phone) {
        String d = digits(phone);
        if (d == null || d.length() < 7) {
            return "***";
        }
        return d.substring(0, 3) + "-****-" + d.substring(d.length() - 4);
    }

    /** 평문 연락처 표시(하이픈 넣기 — 서울 02 · 휴대폰·지역번호 3자리) */
    public static String formatPhone(String phone) {
        String d = digits(phone);
        if (d == null) {
            return null;
        }
        if (d.startsWith("02") && (d.length() == 9 || d.length() == 10)) {
            return "02-" + d.substring(2, d.length() - 4) + "-" + d.substring(d.length() - 4);
        }
        if (d.length() == 10 || d.length() == 11) {
            return d.substring(0, 3) + "-" + d.substring(3, d.length() - 4) + "-" + d.substring(d.length() - 4);
        }
        return d;
    }

    /** 이메일 — 앞 두 글자 · *** · @도메인 */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }

    /** 주소 앞 두 어절 */
    public static String maskAddress(String address) {
        if (address == null) {
            return null;
        }
        String[] words = address.strip().split("\s+");
        return words.length <= 2 ? String.join(" ", words) : words[0] + " " + words[1] + " …";
    }

    /** 이메일 정규화(앞뒤 공백 · 소문자) */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
