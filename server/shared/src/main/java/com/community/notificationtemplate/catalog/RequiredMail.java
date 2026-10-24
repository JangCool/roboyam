package com.community.notificationtemplate.catalog;

import java.util.Arrays;
import java.util.Optional;

/**
 * 필수 안내 메일 — 이벤트가 아닌 꼭 가는 메일(받는 사람이 끌 수 없다). 채널은 이메일뿐이다. 앞의 넷은 <b>보안 메일</b>이라 필수 변수(링크·임시 비밀번호·승인번호)를 지우면 저장되지 않는다.
 * 코드는 개정 전 「메일 종류」의 코드 그대로다(한 번 정하면 바꾸지 않는다 — 문구·선택 행이 이 값으로 가리킨다).
 */
public enum RequiredMail {

    MEMBER_EMAIL_VERIFY(MessageAudience.MEMBER, true),
    OPERATOR_INVITE(MessageAudience.OPERATOR, true),
    OPERATOR_CREDENTIALS(MessageAudience.OPERATOR, true),
    OPERATOR_RESET_CODE(MessageAudience.OPERATOR, true),
    MAIL_TEST(MessageAudience.OPERATOR, false),
    // 회원 계정 흐름(docs/features/member-account-contract.md) — 보안 메일: 비밀번호 재설정 링크 · 소셜 전용 회원 안내 · 이메일 변경 링크(새 주소) · 이메일 변경 알림(지금 주소)
    MEMBER_PASSWORD_RESET(MessageAudience.MEMBER, true),
    MEMBER_PASSWORD_SOCIAL(MessageAudience.MEMBER, true),
    MEMBER_EMAIL_CHANGE(MessageAudience.MEMBER, true),
    MEMBER_EMAIL_CHANGE_NOTICE(MessageAudience.MEMBER, true);

    private final MessageAudience audience;
    private final boolean security;

    RequiredMail(MessageAudience audience, boolean security) {
        this.audience = audience;
        this.security = security;
    }

    public String code() {
        return name();
    }

    public MessageAudience audience() {
        return audience;
    }

    public boolean security() {
        return security;
    }

    public String nameKey() {
        return "notificationTemplate.requiredMail." + name() + ".name";
    }

    public String descriptionKey() {
        return "notificationTemplate.requiredMail." + name() + ".description";
    }

    public static Optional<RequiredMail> ofCode(String code) {
        return Arrays.stream(values()).filter(mail -> mail.name().equals(code)).findFirst();
    }
}
