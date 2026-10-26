package com.community.portone.dto.request;

/**
 * 포트원 본인인증 칸 저장({@code PUT /api/admin/portone-settings/identity}) — <b>보낸 칸만</b> 바꾼다(null = 안 보낸 칸, 그대로 둔다). 검사는 서비스가 칸별로 한다
 * ({@code fields.storeId|apiSecret|identityChannelKey}: 빈 값·500자 초과, 아무 칸도 없으면 400 {@code VALIDATION}). {@link #toString()} 은 값을 싣지 않는다.
 */
public record PortOneIdentitySaveRequest(String storeId, String apiSecret, String identityChannelKey) {

    @Override
    public String toString() {
        return "PortOneIdentitySaveRequest[storeId=" + mark(storeId) + ", apiSecret=" + mark(apiSecret) + ", identityChannelKey=" + mark(identityChannelKey) + "]";
    }

    private static String mark(String value) {
        return value == null ? "null" : "(감춤)";
    }
}
