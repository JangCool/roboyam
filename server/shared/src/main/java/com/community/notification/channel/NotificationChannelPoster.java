package com.community.notification.channel;

import com.community.notification.entity.NotificationChannelEntity;
import com.community.notification.net.OutboundTargetGuard.BlockedTargetException;
import com.community.notification.net.OutboundTargetGuard.Reason;
import com.community.notification.net.SafeHttpClient;
import com.community.notification.net.SafeHttpClient.Response;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.CommonException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import com.community.notificationtemplate.engine.RenderedChat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 도구(Slack · Mattermost)로 글 한 건을 올린다 — 이벤트 발송과 연결 시험이 <b>같은 코드</b>를 쓴다(시험은 되는데 알림은 안 가는 일이 없게).
 *
 * <ul>
 *   <li>비밀(봇 토큰·웹훅 주소)은 올릴 때만 풀고 어디에도 남기지 않는다. 키가 없거나 풀 수 없으면 {@code CRYPTO_KEY_MISSING}·{@code DECRYPT_FAILED}.</li>
 *   <li>모든 요청은 {@link SafeHttpClient}(SSRF 방어)를 지난다. Slack 은 200 이어도 본문의 {@code ok:false} 면 실패다.</li>
 *   <li>결과는 {@link ChannelPostResult} — 성공/실패와 <b>짧은 사유 코드</b>뿐이다. 외부 응답 본문·예외 문구·주소는 결과·로그에 싣지 않는다(Slack 의 오류 이름도 정해진 코드로만 옮긴다).</li>
 *   <li>던지지 않는다. 트랜잭션 밖에서 부른다(느린 외부 호출이 DB 연결을 잡지 않게).</li>
 * </ul>
 */
@Component
public class NotificationChannelPoster {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelPoster.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SafeHttpClient http;
    private final SecretEncryptor encryptor;
    private final String slackApiUrl;

    public NotificationChannelPoster(SafeHttpClient http, SecretEncryptor encryptor,
                                     @Value("${roboyam.notification.slack-api-url:https://slack.com/api/chat.postMessage}") String slackApiUrl) {
        this.http = http;
        this.encryptor = encryptor;
        this.slackApiUrl = slackApiUrl;
    }

    /** 글을 올린다 */
    public ChannelPostResult post(NotificationChannelEntity channel, String text) {
        return send(channel, Map.<String, Object>of("text", text), Map.<String, Object>of("text", text), Map.<String, Object>of("message", text));
    }

    /**
     * 알림 템플릿 렌더링 엔진이 그린 메시지 카드(첨부 하나)를 올린다 — 연결 시험·글 올리기와 같은 길(SSRF 방어 · 사유 코드만)이다.
     * Slack 은 {@code chat.postMessage} 의 {@code attachments}, Mattermost 웹훅은 {@code attachments}, 봇 API 는 {@code props.attachments} 로 감싼다.
     */
    public ChannelPostResult post(NotificationChannelEntity channel, RenderedChat message) {
        List<Map<String, Object>> attachments = List.of(message.attachment());
        Map<String, Object> bot = new LinkedHashMap<>();
        bot.put("message", "");
        bot.put("props", Map.of("attachments", attachments));
        return send(channel, Map.<String, Object>of("attachments", attachments), Map.<String, Object>of("attachments", attachments), bot);
    }

    /**
     * @param slackFields   Slack {@code chat.postMessage} 본문에 더할 칸(채널·펼치기 끄기는 여기서 붙인다)
     * @param webhookFields Mattermost 웹훅 본문
     * @param botFields     Mattermost 봇 API 본문에 더할 칸(채널 id 는 여기서 붙인다)
     */
    private ChannelPostResult send(NotificationChannelEntity channel, Map<String, Object> slackFields, Map<String, Object> webhookFields, Map<String, Object> botFields) {
        try {
            return switch (channel.getChannelType()) {
                case SLACK -> slack(channel, slackFields);
                case MATTERMOST -> mattermost(channel, webhookFields, botFields);
            };
        } catch (BlockedTargetException e) {
            return ChannelPostResult.failure(e.reason() == Reason.DNS ? ChannelPostResult.DNS_FAILED : ChannelPostResult.BLOCKED_TARGET);
        } catch (SocketTimeoutException e) {
            return ChannelPostResult.failure(ChannelPostResult.TIMEOUT);
        } catch (SSLException e) {
            return ChannelPostResult.failure(ChannelPostResult.TLS_FAILED);
        } catch (UnknownHostException e) {
            return ChannelPostResult.failure(ChannelPostResult.DNS_FAILED);
        } catch (ConnectException | NoRouteToHostException e) {
            return ChannelPostResult.failure(ChannelPostResult.CONNECT_FAILED);
        } catch (IOException e) {
            return ChannelPostResult.failure(ChannelPostResult.NETWORK_ERROR);
        } catch (CommonException e) {
            return ChannelPostResult.failure(ChannelPostResult.CRYPTO_KEY_MISSING.equals(e.getErrorCode()) ? ChannelPostResult.CRYPTO_KEY_MISSING : ChannelPostResult.ERROR);
        } catch (DecryptFailedException e) {
            return ChannelPostResult.failure(ChannelPostResult.DECRYPT_FAILED);
        } catch (RuntimeException e) {
            // 예외 문구에 주소·토큰이 실릴 수 있어 클래스 이름만 남긴다
            log.error("알림 도구 게시 중 예상하지 못한 오류 원인={}", e.getClass().getSimpleName());
            return ChannelPostResult.failure(ChannelPostResult.ERROR);
        }
    }

    private ChannelPostResult slack(NotificationChannelEntity channel, Map<String, Object> fields) throws IOException {
        String token = secret(channel.getBotTokenEnc());
        String room = channel.getChannelTarget();
        if (token == null || room == null || room.isBlank()) {
            return ChannelPostResult.failure(ChannelPostResult.NOT_CONFIGURED);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", room);
        payload.putAll(fields);
        payload.put("mrkdwn", true);
        payload.put("unfurl_links", false);
        payload.put("unfurl_media", false);
        Response response = http.postJson(slackApiUrl, Map.of("Authorization", "Bearer " + token), JSON.writeValueAsString(payload));
        ChannelPostResult byStatus = byStatus(response.status());
        if (!byStatus.ok()) {
            return byStatus;
        }
        // 200 이어도 본문의 ok:false 면 안 간 것이다 — 본문은 여기서만 읽고 돌려주지 않는다
        Map<?, ?> body = parseObject(response.body());
        if (!Boolean.TRUE.equals(body.get("ok"))) {
            return ChannelPostResult.failure(slackError(String.valueOf(body.get("error"))));
        }
        return ChannelPostResult.success();
    }

    private ChannelPostResult mattermost(NotificationChannelEntity channel, Map<String, Object> webhookFields, Map<String, Object> botFields) throws IOException {
        if ("webhook".equals(channel.getMattermostMode())) {
            String url = secret(channel.getWebhookUrlEnc());
            if (url == null) {
                return ChannelPostResult.failure(ChannelPostResult.NOT_CONFIGURED);
            }
            Response response = http.postJson(url, Map.of(), JSON.writeValueAsString(webhookFields));
            return byStatus(response.status());
        }
        String token = secret(channel.getBotTokenEnc());
        String server = channel.getServerUrl();
        String room = channel.getChannelTarget();
        if (token == null || server == null || server.isBlank() || room == null || room.isBlank()) {
            return ChannelPostResult.failure(ChannelPostResult.NOT_CONFIGURED);
        }
        String base = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel_id", room);
        payload.putAll(botFields);
        Response response = http.postJson(base + "/api/v4/posts", Map.of("Authorization", "Bearer " + token), JSON.writeValueAsString(payload));
        return byStatus(response.status());
    }

    /** 암호문을 푼다. 값이 없으면 null (호출하는 쪽이 「설정 부족」으로 본다) */
    private String secret(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        if (!encryptor.isConfigured()) {
            throw new CommonException(com.community.zconfiguration.exception.ErrorCode.CRYPTO_KEY_MISSING);
        }
        try {
            String plain = encryptor.decrypt(cipherText);
            return plain == null || plain.isBlank() ? null : plain;
        } catch (IllegalArgumentException e) {
            throw new DecryptFailedException();
        }
    }

    /** 비밀을 풀지 못했다 — 키가 저장 때와 다르거나 암호문이 깨졌다 */
    private static final class DecryptFailedException extends RuntimeException {
        DecryptFailedException() {
            super("decrypt", null, false, false);
        }
    }

    private static ChannelPostResult byStatus(int status) {
        if (status / 100 == 2) {
            return ChannelPostResult.success();
        }
        if (status == 401 || status == 403) {
            return ChannelPostResult.failure(ChannelPostResult.AUTH_FAILED);
        }
        if (status == 404) {
            return ChannelPostResult.failure(ChannelPostResult.NOT_FOUND);
        }
        if (status == 429) {
            return ChannelPostResult.failure(ChannelPostResult.RATE_LIMITED);
        }
        if (status / 100 == 3) {
            return ChannelPostResult.failure(ChannelPostResult.REDIRECTED);
        }
        if (status / 100 == 5) {
            return ChannelPostResult.failure(ChannelPostResult.SERVER_ERROR);
        }
        return ChannelPostResult.failure(ChannelPostResult.REJECTED);
    }

    /** Slack 오류 이름을 정해진 코드로만 옮긴다(받은 글을 그대로 내보내지 않는다) */
    static String slackError(String error) {
        return switch (error) {
            case "invalid_auth", "not_authed", "token_revoked", "token_expired", "account_inactive", "invalid_token" -> ChannelPostResult.AUTH_FAILED;
            case "channel_not_found", "not_in_channel", "is_archived" -> ChannelPostResult.CHANNEL_NOT_FOUND;
            case "missing_scope", "no_permission", "restricted_action", "access_denied", "org_login_required" -> ChannelPostResult.FORBIDDEN;
            case "ratelimited", "rate_limited" -> ChannelPostResult.RATE_LIMITED;
            default -> ChannelPostResult.REJECTED;
        };
    }

    private static Map<?, ?> parseObject(String body) {
        try {
            Object parsed = JSON.readValue(body, Map.class);
            return parsed instanceof Map<?, ?> map ? map : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
