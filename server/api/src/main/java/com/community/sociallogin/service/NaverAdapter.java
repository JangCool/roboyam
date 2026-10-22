package com.community.sociallogin.service;

import static com.community.sociallogin.service.SocialProviderAdapter.text;

import com.community.social.entity.SocialProvider;
import com.community.sociallogin.config.SocialEndpoints.Endpoints;
import com.community.sociallogin.dto.SocialProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 네이버 로그인 — 인가 코드 + state. 네이버는 PKCE·nonce 를 받지 않는다(state 만으로 막는다).
 * 네이버는 이메일을 확인했는지 알려 주지 않으므로 <b>늘 확인되지 않은 이메일</b>로 본다(가입 뒤 이메일 인증 흐름). 닉네임 후보는 별명이다(실명 {@code name} 은 쓰지 않는다).
 * 토큰 오류는 상태 200 에 {@code error} 칸으로 오기도 한다 — 액세스 토큰이 없으면 실패다.
 */
@Component
public class NaverAdapter implements SocialProviderAdapter {

    private final SocialHttp http;

    public NaverAdapter(SocialHttp http) {
        this.http = http;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public boolean usesPkce() {
        return false;
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
        return SocialProviderAdapter.withQuery(endpoints.authorizeUrl(), params);
    }

    @Override
    public SocialProfile fetchProfile(Endpoints endpoints, ClientCredentials credentials, String redirectUri, String code, String state,
                                      String codeVerifier, String nonceHash) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", credentials.clientId());
        form.put("client_secret", credentials.clientSecret());
        form.put("code", code);
        form.put("state", state);
        SocialHttp.Response token = http.postForm(endpoints.tokenUrl(), form);
        String accessToken = token.ok() ? text(token.json(), "access_token") : null;
        if (accessToken == null || accessToken.isBlank()) {
            throw new SocialProviderException("토큰 교환 실패 status=" + token.status());
        }

        SocialHttp.Response me = http.getWithBearer(endpoints.userInfoUrl(), accessToken);
        if (!me.ok() || !"00".equals(text(me.json(), "resultcode"))) {
            throw new SocialProviderException("회원 정보 실패 status=" + me.status());
        }
        JsonNode response = me.json().get("response");
        String subject = SocialProviderAdapter.subject(response, "id");
        String email = SocialProviderAdapter.email(text(response, "email"));
        String nickname = SocialProviderAdapter.nicknameCandidate(text(response, "nickname"));
        return new SocialProfile(SocialProvider.NAVER, subject, email, false, nickname);
    }
}
