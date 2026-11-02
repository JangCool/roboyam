package com.community.publicdata.dto.request;

/**
 * {@code PUT /api/admin/public-data-settings} — 공공데이터포털 일반 인증키(Decoding). 검사는 서비스가 한다(400 {@code fields.serviceKey}).
 * 값은 이 요청으로만 지나가고 응답·감사·로그에 남지 않는다.
 */
public record PublicDataSaveRequest(String serviceKey) {

    @Override
    public String toString() {
        return "PublicDataSaveRequest[serviceKey=" + (serviceKey == null ? "null" : "***") + "]";
    }
}
