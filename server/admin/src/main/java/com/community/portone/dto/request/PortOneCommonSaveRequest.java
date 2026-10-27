package com.community.portone.dto.request;

/**
 * 포트원 공통 카드 저장({@code PUT /api/admin/portone-settings/common}) — 상점 ID·API 비밀키(본인인증·결제가 같이 쓴다, 2a 결정 14). <b>보낸 칸만</b> 바꾼다(null = 안 보낸 칸).
 * 검사는 서비스가 칸별로 한다({@code fields.storeId|apiSecret}: 빈 값·500자 초과, 아무 칸도 없으면 400 {@code VALIDATION}). {@link #toString()} 은 값을 싣지 않는다.
 */
public record PortOneCommonSaveRequest(String storeId, String apiSecret) {

    @Override
    public String toString() {
        return "PortOneCommonSaveRequest[storeId=" + mark(storeId) + ", apiSecret=" + mark(apiSecret) + "]";
    }

    private static String mark(String value) {
        return value == null ? "null" : "(감춤)";
    }
}
