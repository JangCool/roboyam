package com.community.portone.dto;

/**
 * 포트원 결제에 쓰는 설정 값(복호화한 값) — 결제 게이트웨이만 잠깐 쓴다. 응답·로그·감사에 싣지 않는다({@code toString} 은 API 비밀키·웹훅 비밀키를 감춘다).
 *
 * @param storeId       상점 ID(공통)
 * @param apiSecret     V2 API 비밀키(공통)
 * @param channelKey    결제 채널 키(결정 15 — 하나)
 * @param webhookSecret 웹훅 비밀키(Standard Webhooks, {@code whsec_…})
 */
public record PortOnePaymentCredentials(String storeId, String apiSecret, String channelKey, String webhookSecret) {

    @Override
    public String toString() {
        return "PortOnePaymentCredentials[storeId=" + storeId + ", apiSecret=(감춤), channelKey=" + channelKey + ", webhookSecret=(감춤)]";
    }
}
