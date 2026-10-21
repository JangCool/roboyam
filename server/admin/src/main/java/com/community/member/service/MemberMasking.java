package com.community.member.service;

/**
 * 회원 개인정보 가림 규칙 — {@code member.pii} 권한이 없는 운영자에게 가려서 준다. 이메일은 앞 두 글자(로컬 파트가 두 글자 이하면 한 글자)만 남기고 {@code ***} 로 가린 뒤 도메인을 붙인다
 * ({@code ab***@gmail.com}) — admin-web 목업 {@code maskEmail} 과 같다. 휴대폰·전화·주소는 해당 칸이 생길 때 정한다(정책 미확정).
 */
public final class MemberMasking {

    private MemberMasking() {
    }

    /** {@code kim123@gmail.com} → {@code ki***@gmail.com}. 비어 있으면 null */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return "***";
        }
        String local = email.substring(0, at);
        int keep = local.length() <= 2 ? 1 : 2;
        return local.substring(0, Math.min(keep, local.length())) + "***" + email.substring(at);
    }
}
