package com.community.portone.dto.request;

/**
 * 포트원 결제 카드 저장({@code PUT /api/admin/portone-settings/payment}) — 결제 채널 키·웹훅 비밀키(2a 결정 14·15). <b>보낸 칸만</b> 바꾼다(null = 안 보낸 칸).
 * 검사는 서비스가 칸별로 한다({@code fields.paymentChannelKey|webhookSecret}: 빈 값·500자 초과, 웹훅 비밀키는 {@code whsec_} + base64). {@link #toString()} 은 값을 싣지 않는다.
 */
public record PortOnePaymentSaveRequest(String paymentChannelKey, String webhookSecret) {

    @Override
    public String toString() {
        return "PortOnePaymentSaveRequest[paymentChannelKey=" + mark(paymentChannelKey) + ", webhookSecret=" + mark(webhookSecret) + "]";
    }

    private static String mark(String value) {
        return value == null ? "null" : "(감춤)";
    }
}
