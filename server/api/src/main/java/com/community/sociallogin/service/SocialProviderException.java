package com.community.sociallogin.service;

/**
 * 공급자와의 교환이 실패했다 — 응답이 아니라 콜백 서비스 안에서만 쓰는 신호다(사용자 앱에는 {@code reason=provider_error} 로만 돌려보낸다).
 * 문구는 서버 로그용 짧은 사유이고 공급자 응답 본문·토큰·코드·시크릿을 싣지 않는다. 스택은 남기지 않는다.
 */
public class SocialProviderException extends RuntimeException {

    public SocialProviderException(String reason) {
        super(reason, null, false, false);
    }
}
