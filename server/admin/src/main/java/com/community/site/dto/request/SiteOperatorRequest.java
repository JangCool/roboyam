package com.community.site.dto.request;

/**
 * {@code PUT /api/admin/site/operator} — 운영 주체 값 한 벌(admin-web {@code SiteOperatorRequest}). 검사는 서비스가 칸마다 모아 {@code 400 VALIDATION}
 * ({@code fields.stage} · {@code operatorName} · … · 사업자 칸은 {@code business.businessName} 꼴).
 *
 * @param stage    {@code INDIVIDUAL} · {@code SOLE_PROPRIETOR} · {@code CORPORATION}
 * @param business 사업자 정보 — 사업자 단계에서만 읽는다(개인 단계면 무시하고 비운다)
 */
public record SiteOperatorRequest(String stage, String operatorName, String contactEmail, String phone, Boolean privacyOfficerSameAsOperator,
                                  String privacyOfficerName, String privacyOfficerEmail, String hostingProvider, Business business) {

    /** @param corporateNumber 법인등록번호 — 법인 단계에서만 읽는다 */
    public record Business(String businessName, String representative, String registrationNumber, String address, String mailOrderNumber,
                           String mailOrderAuthority, String corporateNumber) {
    }

    @Override
    public String toString() {
        return "SiteOperatorRequest[stage=" + stage + "]";
    }
}
