package com.community.notificationtemplate.catalog;

import java.util.Arrays;
import java.util.Optional;

/** 메일 템플릿을 쓰는 대상 — 받는 사람 하나 또는 모두({@link #ANY} — 틀·꼬리말에서 두 받는 사람이 같이 쓰는 공통 변수 {@code siteName}·{@code recipientName} 만) */
public enum MailTemplateAudience {

    MEMBER,
    OPERATOR,
    ANY;

    /** 이 받는 사람의 이메일 문구가 이 쓰는 대상의 메일 템플릿을 쓸 수 있는가 — 같거나 {@code ANY} */
    public boolean fits(MessageAudience audience) {
        return this == ANY || name().equals(audience.name());
    }

    /** 받는 사람 하나짜리면 그 받는 사람, {@code ANY} 면 비어 있다 */
    public Optional<MessageAudience> single() {
        return this == ANY ? Optional.empty() : MessageAudience.of(name());
    }

    public static MailTemplateAudience of(MessageAudience audience) {
        return valueOf(audience.name());
    }

    public static Optional<MailTemplateAudience> of(String name) {
        return Arrays.stream(values()).filter(audience -> audience.name().equals(name)).findFirst();
    }
}
