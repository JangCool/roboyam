package com.community.captcha;

import com.community.notification.net.SafeHttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cloudflare Turnstile 서버 확인({@code siteverify}). 외부 요청은 {@link SafeHttpClient}(짧은 제한 시간·리다이렉트 안 따라감)로 한다.
 * 공급자에 닿지 못하거나 응답을 읽지 못하면 <b>실패(false)</b>로 본다 — 비회원 쓰기를 막는 쪽이 안전하다. 토큰·비밀키·응답 본문은 로그에 남기지 않는다.
 */
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    /** Turnstile 토큰 최대 길이(공급자 문서 2048) — 넘으면 보내지 않고 실패 */
    private static final int TOKEN_MAX = 2048;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final SafeHttpClient http;
    private final String secret;

    public TurnstileCaptchaVerifier(SafeHttpClient http, String secret) {
        this.http = http;
        this.secret = secret;
    }

    @Override
    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank() || token.length() > TOKEN_MAX) {
            return false;
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("secret", secret);
        body.put("response", token);
        if (remoteIp != null) {
            body.put("remoteip", remoteIp);
        }
        try {
            SafeHttpClient.Response response = http.postJson(VERIFY_URL, Map.of(), MAPPER.writeValueAsString(body));
            if (response.status() != 200) {
                log.warn("Turnstile 확인 응답이 200 이 아니다: {}", response.status());
                return false;
            }
            JsonNode node = MAPPER.readTree(response.body());
            return node != null && node.path("success").asBoolean(false);
        } catch (Exception e) {
            log.warn("Turnstile 확인에 실패했다: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
