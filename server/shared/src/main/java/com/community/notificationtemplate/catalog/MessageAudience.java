package com.community.notificationtemplate.catalog;

import java.util.Arrays;
import java.util.Optional;

/**
 * 문구의 받는 사람 — 회원(공개 사이트) · 운영자(관리 사이트). 대상이 정하고, 받는 사람이 공통 변수(회원 {@code siteUrl} · 운영자 {@code adminUrl})와
 * 고를 수 있는 메일 템플릿({@link MailTemplateAudience#fits})을 정한다.
 */
public enum MessageAudience {

    MEMBER,
    OPERATOR;

    public static Optional<MessageAudience> of(String name) {
        return Arrays.stream(values()).filter(audience -> audience.name().equals(name)).findFirst();
    }
}
