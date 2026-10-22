package com.community.sociallogin.service;

import com.community.social.entity.SocialProvider;
import com.community.sociallogin.config.SocialEndpoints.Endpoints;
import com.community.sociallogin.dto.SocialProfile;
import com.community.user.service.UserPolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import tools.jackson.databind.JsonNode;

/**
 * 공급자 한 곳의 OAuth 2.0 인가 코드 흐름 — 인가 주소 만들기와, 코드를 회원 정보로 바꾸기. 주소는 {@code SocialEndpoints}(코드 고정)에서 온다.
 * 공급자 액세스 토큰은 회원 정보를 읽는 데만 쓰고 저장하지 않는다. 실패는 {@link SocialProviderException} 이고 사유에 응답 본문·토큰을 싣지 않는다.
 */
public interface SocialProviderAdapter {

    /** 공급자 회원 번호 최대 길이({@code user_identities.provider_user_id varchar(128)}) */
    int SUBJECT_MAX = 128;

    SocialProvider provider();

    /** PKCE(S256)를 보낼 것인가 */
    boolean usesPkce();

    /** OpenID Connect nonce 를 보내고 ID 토큰에서 확인할 것인가 */
    boolean usesNonce();

    /** 공급자 인가 주소. {@code codeChallenge}·{@code nonce} 는 쓰지 않는 공급자면 null 이다 */
    String authorizeUrl(Endpoints endpoints, String clientId, String redirectUri, String state, String codeChallenge, String nonce);

    /**
     * 인가 코드를 회원 정보로 바꾼다(토큰 교환 → 회원 정보·ID 토큰 확인).
     *
     * @param nonceHash 시작 때 보낸 nonce 의 SHA-256 — nonce 를 쓰지 않는 공급자면 null
     * @throws SocialProviderException 교환·확인 실패
     */
    SocialProfile fetchProfile(Endpoints endpoints, ClientCredentials credentials, String redirectUri, String code, String state,
                               String codeVerifier, String nonceHash);

    /** 클라이언트 ID·복호화한 시크릿 — 이 요청 안에서만 쓴다. {@link #toString()} 은 시크릿을 감춘다 */
    record ClientCredentials(String clientId, String clientSecret) {

        @Override
        public String toString() {
            return "ClientCredentials[clientId=" + clientId + ", clientSecret=(감춤)]";
        }
    }

    /** 쿼리 문자열 — 값이 null 인 키는 뺀다 */
    static String withQuery(String url, Map<String, String> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((key, value) -> {
            if (value != null) {
                query.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    /** 문자열 칸(없거나 문자열이 아니면 null) */
    static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    /** 참/거짓 칸 — {@code true} 또는 문자열 {@code "true"} 만 참이다 */
    static boolean bool(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        JsonNode value = node.get(field);
        if (value == null) {
            return false;
        }
        return value.isBoolean() ? value.booleanValue() : value.isString() && "true".equalsIgnoreCase(value.asString());
    }

    /** 회원 번호 — 문자열이거나 정수인 칸. 비었거나 128자를 넘거나 제어 문자가 있으면 실패 */
    static String subject(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        String subject = null;
        if (value != null && value.isString()) {
            subject = value.asString();
        } else if (value != null && value.isIntegralNumber()) {
            subject = value.bigIntegerValue().toString();
        }
        if (subject == null || subject.isBlank() || subject.length() > SUBJECT_MAX || subject.chars().anyMatch(c -> c < 0x21 || c == 0x7f)) {
            throw new SocialProviderException("회원 번호가 없거나 형식이 다르다");
        }
        return subject;
    }

    /** 이메일 — 소문자로 바꾸고 형식이 맞을 때만, 아니면 null */
    static String email(String raw) {
        String email = UserPolicy.normalizeEmail(raw);
        return email != null && UserPolicy.isValidEmail(email) ? email : null;
    }

    /** 닉네임 후보 — 앞뒤 공백을 자르고 닉네임 규칙에 맞을 때만, 아니면 null */
    static String nicknameCandidate(String raw) {
        if (raw == null) {
            return null;
        }
        String nickname = raw.strip();
        return UserPolicy.nicknameViolation(nickname).isEmpty() ? nickname : null;
    }
}
