package com.community.groupbuy.feedback;

import java.util.regex.Pattern;

/**
 * 공구 문의 본문의 개인정보 모양(사용자 결정 26 — 자동 비밀글). 전화번호 · 이메일 · 도로명·지번 주소 모양을 찾는다. 놓치는 것이 있어도 막지는 않는다(비밀글로 돌리는 편의).
 */
public final class PersonalInfo {

    /** 휴대전화·일반 전화(구분 문자 -·.·공백 허용) */
    private static final Pattern PHONE = Pattern.compile("(01[016789]|0[2-6][0-9]?)[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    /** 「OO시/도/구/군 + OO로/길/동 + 번지」 */
    private static final Pattern ADDRESS = Pattern.compile("\\S+(시|도|구|군)\\s+\\S+(로|길|동|읍|면)\\s*\\d+");

    private PersonalInfo() {
    }

    public static boolean looksPersonal(String text) {
        return text != null && (PHONE.matcher(text).find() || EMAIL.matcher(text).find() || ADDRESS.matcher(text).find());
    }

    /** 닉네임 가리기 — 첫 글자 + {@code ***}(탈퇴는 null) */
    public static String maskNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname.codePoints().limit(1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append) + "***";
    }
}
