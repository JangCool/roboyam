package com.community.sociallogin.service;

import static com.community.sociallogin.service.SocialProviderAdapter.bool;
import static com.community.sociallogin.service.SocialProviderAdapter.text;

import com.community.social.entity.SocialProvider;
import com.community.sociallogin.config.SocialEndpoints.Endpoints;
import com.community.sociallogin.dto.SocialProfile;
import com.community.zconfiguration.crypto.Digests;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구글 로그인(OpenID Connect) — 인가 코드 + PKCE(S256) + nonce. 회원 정보는 토큰 응답의 <b>ID 토큰</b>에서 읽고 따로 묻지 않는다.
 *
 * <p>ID 토큰 확인: 발급자({@code iss} — {@code https://accounts.google.com}·{@code accounts.google.com}), 대상({@code aud} 가 우리 클라이언트 ID,
 * 여럿이면 {@code azp} 도 우리 ID), 만료({@code exp}, 시계 차이 60초), 발급 시각({@code iat} 가 5분 넘게 미래가 아님), {@code nonce}(시작 때 보낸 값의 해시와 같음),
 * {@code sub}. 서명({@code alg})은 확인하지 않는다 — ID 토큰을 <b>TLS 로 토큰 엔드포인트에서 직접</b> 받았으므로 OpenID Connect Core 3.1.3.7 6항에 따라
 * TLS 서버 확인으로 발급자 확인을 대신한다(다른 경로로 받은 ID 토큰은 쓰지 않는다). {@code alg=none} 은 거절한다.
 * 이메일은 {@code email_verified} 가 참일 때만 확인된 이메일이다. 실명({@code name})은 닉네임 후보로 쓰지 않는다.
 */
@Component
public class GoogleAdapter implements SocialProviderAdapter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final long MAX_IAT_FUTURE_SECONDS = 300;
    private static final int ID_TOKEN_MAX = 16 * 1024;

    private final SocialHttp http;

    public GoogleAdapter(SocialHttp http) {
        this.http = http;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public boolean usesPkce() {
        return true;
    }

    @Override
    public boolean usesNonce() {
        return true;
    }

    @Override
    public String authorizeUrl(Endpoints endpoints, String clientId, String redirectUri, String state, String codeChallenge, String nonce) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("scope", "openid email profile");
        params.put("state", state);
        params.put("nonce", nonce);
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        return SocialProviderAdapter.withQuery(endpoints.authorizeUrl(), params);
    }

    @Override
    public SocialProfile fetchProfile(Endpoints endpoints, ClientCredentials credentials, String redirectUri, String code, String state,
                                      String codeVerifier, String nonceHash) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", credentials.clientId());
        form.put("client_secret", credentials.clientSecret());
        form.put("redirect_uri", redirectUri);
        form.put("code", code);
        form.put("code_verifier", codeVerifier);
        SocialHttp.Response token = http.postForm(endpoints.tokenUrl(), form);
        String idToken = token.ok() ? text(token.json(), "id_token") : null;
        if (idToken == null || idToken.isBlank() || idToken.length() > ID_TOKEN_MAX) {
            throw new SocialProviderException("토큰 교환 실패 status=" + token.status());
        }
        JsonNode claims = verifiedClaims(idToken, endpoints, credentials.clientId(), nonceHash, Instant.now());

        String subject = SocialProviderAdapter.subject(claims, "sub");
        String email = SocialProviderAdapter.email(text(claims, "email"));
        boolean verified = email != null && bool(claims, "email_verified");
        return new SocialProfile(SocialProvider.GOOGLE, subject, email, verified, null);
    }

    /** ID 토큰의 내용을 읽고 확인한다. 어긋나면 실패(사유에 토큰 값을 싣지 않는다) */
    static JsonNode verifiedClaims(String idToken, Endpoints endpoints, String clientId, String nonceHash, Instant now) {
        String[] parts = idToken.split("\\.", -1);
        if (parts.length != 3) {
            throw new SocialProviderException("ID 토큰 형식");
        }
        JsonNode header = decode(parts[0]);
        String alg = text(header, "alg");
        if (alg == null || "none".equalsIgnoreCase(alg)) {
            throw new SocialProviderException("ID 토큰 alg");
        }
        JsonNode claims = decode(parts[1]);

        String issuer = text(claims, "iss");
        if (issuer == null || !endpoints.issuers().contains(issuer)) {
            throw new SocialProviderException("ID 토큰 iss");
        }
        JsonNode aud = claims.get("aud");
        boolean audienceOk;
        if (aud != null && aud.isString()) {
            audienceOk = clientId.equals(aud.asString());
        } else if (aud != null && aud.isArray() && !aud.isEmpty()) {
            boolean contains = false;
            for (JsonNode one : aud) {
                contains |= one.isString() && clientId.equals(one.asString());
            }
            audienceOk = contains && (aud.size() == 1 || clientId.equals(text(claims, "azp")));
        } else {
            audienceOk = false;
        }
        String azp = text(claims, "azp");
        if (!audienceOk || (azp != null && !clientId.equals(azp))) {
            throw new SocialProviderException("ID 토큰 aud");
        }
        long nowSeconds = now.getEpochSecond();
        JsonNode exp = claims.get("exp");
        if (exp == null || !exp.isNumber() || exp.longValue() + CLOCK_SKEW_SECONDS <= nowSeconds) {
            throw new SocialProviderException("ID 토큰 exp");
        }
        JsonNode iat = claims.get("iat");
        if (iat == null || !iat.isNumber() || iat.longValue() > nowSeconds + MAX_IAT_FUTURE_SECONDS) {
            throw new SocialProviderException("ID 토큰 iat");
        }
        String nonce = text(claims, "nonce");
        if (nonce == null || nonceHash == null
                || !MessageDigest.isEqual(Digests.sha256Hex(nonce).getBytes(StandardCharsets.US_ASCII), nonceHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new SocialProviderException("ID 토큰 nonce");
        }
        return claims;
    }

    private static JsonNode decode(String part) {
        try {
            JsonNode node = MAPPER.readTree(Base64.getUrlDecoder().decode(part));
            if (node == null || !node.isObject()) {
                throw new SocialProviderException("ID 토큰 형식");
            }
            return node;
        } catch (SocialProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SocialProviderException("ID 토큰 형식");
        }
    }
}
