package com.community.sociallogin.dto;

import com.community.social.entity.SocialProvider;
import java.io.Serializable;
import java.time.Instant;

/**
 * 시작한 소셜 흐름 한 건 — <b>서버 세션에만</b> 둔다(세션에 묶인 일회용). 콜백이 오면 꺼내면서 지운다.
 *
 * @param stateHash    state 원문의 SHA-256(16진). 원문은 공급자 주소에만 실린다
 * @param provider     공급자
 * @param mode         로그인 또는 연결
 * @param linkUserId   연결이면 시작한 회원 ID, 로그인이면 null
 * @param codeVerifier PKCE 검증값(공급자가 PKCE 를 받으면). 토큰 교환 때만 보낸다
 * @param nonceHash    구글 ID 토큰의 nonce 원문의 SHA-256. 구글이 아니면 null
 * @param next         로그인 뒤 돌아갈 같은 사이트 상대 경로(검사를 마친 값). 없으면 null
 * @param expiresAt    이 시각이 지나면 쓸 수 없다
 */
public record AuthorizationFlow(String stateHash, SocialProvider provider, SocialMode mode, Long linkUserId, String codeVerifier, String nonceHash,
                                String next, Instant expiresAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    @Override
    public String toString() {
        return "AuthorizationFlow[provider=" + provider + ", mode=" + mode + ", expiresAt=" + expiresAt + "]";
    }
}
