package com.community.sociallogin.dto.response;

/**
 * {@code POST /api/me/social/{provider}/link} — 화면이 {@code location.href = redirectUrl} 로 이동한다(공급자 인가 주소).
 * 주소에 일회용 state 가 들어 있다 — 화면은 저장·기록하지 않는다.
 */
public record SocialLinkStartResponse(String redirectUrl) {

    @Override
    public String toString() {
        return "SocialLinkStartResponse[redirectUrl=(감춤)]";
    }
}
