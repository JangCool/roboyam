package com.community.notification.channel;

import java.util.Set;

/**
 * 알림 도구로 한 번 게시(또는 시험)한 결과 — 성공 여부와 <b>짧은 사유 코드</b>뿐이다. 외부(Slack·Mattermost·메일 서버)의 응답 본문·예외 문구는 담지 않는다.
 * 사유 코드의 문구는 메시지 번들의 {@code notification.channel.test.<코드>} 다(요청 언어로 푼다).
 */
public record ChannelPostResult(boolean ok, String code) {

    public static final String OK = "OK";
    /** 메일 시험 성공 */
    public static final String MAIL_OK = "MAIL_OK";
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String CRYPTO_KEY_MISSING = "CRYPTO_KEY_MISSING";
    public static final String DECRYPT_FAILED = "DECRYPT_FAILED";
    public static final String BLOCKED_TARGET = "BLOCKED_TARGET";
    public static final String DNS_FAILED = "DNS_FAILED";
    public static final String CONNECT_FAILED = "CONNECT_FAILED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String TLS_FAILED = "TLS_FAILED";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String AUTH_FAILED = "AUTH_FAILED";
    public static final String CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String REDIRECTED = "REDIRECTED";
    public static final String SERVER_ERROR = "SERVER_ERROR";
    public static final String REJECTED = "REJECTED";
    public static final String ERROR = "ERROR";
    public static final String MAIL_NOT_CONFIGURED = "MAIL_NOT_CONFIGURED";
    public static final String MAIL_FAILED = "MAIL_FAILED";

    /** 번들에 문구가 있는 코드 — 이 밖의 값은 저장하지 않는다 */
    public static final Set<String> CODES = Set.of(OK, MAIL_OK, NOT_CONFIGURED, CRYPTO_KEY_MISSING, DECRYPT_FAILED, BLOCKED_TARGET, DNS_FAILED, CONNECT_FAILED, TIMEOUT,
            TLS_FAILED, NETWORK_ERROR, AUTH_FAILED, CHANNEL_NOT_FOUND, FORBIDDEN, NOT_FOUND, RATE_LIMITED, REDIRECTED, SERVER_ERROR, REJECTED, ERROR, MAIL_NOT_CONFIGURED,
            MAIL_FAILED);

    public static ChannelPostResult success() {
        return new ChannelPostResult(true, OK);
    }

    public static ChannelPostResult failure(String code) {
        return new ChannelPostResult(false, CODES.contains(code) ? code : ERROR);
    }
}
