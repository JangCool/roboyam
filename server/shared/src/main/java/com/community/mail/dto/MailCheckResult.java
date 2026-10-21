package com.community.mail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 메일 서버 연결 시험 결과 — 접속·인증까지만 해 보고 메일은 보내지 않는다.
 *
 * @param reason 실패 원인({@code CONNECT_FAILED} · {@code AUTH_FAILED} · {@code TLS_FAILED} · {@code TIMEOUT}). 성공이면 응답에서 빠진다.
 *               서버 응답 원문·자격 증명은 싣지 않는다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MailCheckResult(boolean ok, String reason) {

    public static MailCheckResult success() {
        return new MailCheckResult(true, null);
    }

    public static MailCheckResult failure(MailCheckReason reason) {
        return new MailCheckResult(false, reason.name());
    }
}
