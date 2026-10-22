package com.community.sociallogin.service;

import static com.community.sociallogin.service.SocialProviderAdapter.bool;
import static com.community.sociallogin.service.SocialProviderAdapter.text;

import com.community.social.entity.SocialProvider;
import com.community.sociallogin.config.SocialEndpoints.Endpoints;
import com.community.sociallogin.dto.SocialProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 카카오 로그인 — 인가 코드 + PKCE(S256). {@code scope} 는 보내지 않는다(콘솔의 동의 항목을 따른다 — 콘솔에 없는 범위를 보내면 공급자가 거절한다).
 * 이메일은 {@code is_email_valid}·{@code is_email_verified} 가 모두 참일 때만 확인된 이메일로 본다. 닉네임 후보는 프로필 닉네임이다.
 */
@Component
public class KakaoAdapter implements SocialProviderAdapter {

    private final SocialHttp http;

    public KakaoAdapter(SocialHttp http) {
        this.http = http;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public boolean usesPkce() {
        return true;
    }

    @Override
    public boolean usesNonce() {
        return false;
    }

    @Override
    public String authorizeUrl(Endpoints endpoints, String clientId, String redirectUri, String state, String codeChallenge, String nonce) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("state", state);
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
        String accessToken = token.ok() ? text(token.json(), "access_token") : null;
        if (accessToken == null || accessToken.isBlank()) {
            throw new SocialProviderException("토큰 교환 실패 status=" + token.status());
        }

        SocialHttp.Response me = http.getWithBearer(endpoints.userInfoUrl(), accessToken);
        if (!me.ok()) {
            throw new SocialProviderException("회원 정보 실패 status=" + me.status());
        }
        JsonNode body = me.json();
        String subject = SocialProviderAdapter.subject(body, "id");
        JsonNode account = body.get("kakao_account");
        String email = SocialProviderAdapter.email(text(account, "email"));
        boolean verified = email != null && bool(account, "is_email_valid") && bool(account, "is_email_verified");
        String nickname = SocialProviderAdapter.nicknameCandidate(text(account == null ? null : account.get("profile"), "nickname"));
        return new SocialProfile(SocialProvider.KAKAO, subject, email, verified, nickname);
    }
}
