package com.community.mail.entity;

import java.util.Locale;
import java.util.Optional;

/** 메일 서버와의 연결 보안 방식. mail_settings.security 의 CHECK 와 같은 값이다. */
public enum MailSecurity {
    /** 암호화하지 않는다(내 PC 의 메일함 시험 서버처럼 믿을 수 있는 곳에서만) */
    NONE,
    /** 평문으로 접속한 뒤 STARTTLS 로 올린다(보통 587 포트). 서버가 지원하지 않으면 평문으로 물러서지 않고 실패한다 */
    STARTTLS,
    /** 처음부터 TLS 로 접속한다(보통 465 포트) */
    SSL;

    /** 대소문자를 무시하고 이름으로 찾는다. 모르는 이름이면 비어 있다 */
    public static Optional<MailSecurity> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String upper = name.strip().toUpperCase(Locale.ROOT);
        for (MailSecurity value : values()) {
            if (value.name().equals(upper)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
