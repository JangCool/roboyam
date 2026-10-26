package com.community.portone.dto;

/**
 * 포트원 본인인증에 쓰는 설정 값(복호화한 값) — 본인인증 구현만 잠깐 쓴다. 응답·로그·감사에 싣지 않는다({@code toString} 은 API 비밀키를 감춘다).
 */
public record PortOneIdentityCredentials(String storeId, String apiSecret, String channelKey) {

    @Override
    public String toString() {
        return "PortOneIdentityCredentials[storeId=" + storeId + ", apiSecret=(감춤), channelKey=" + channelKey + "]";
    }
}
