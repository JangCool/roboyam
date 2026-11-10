package com.community.site.dto.request;

/**
 * {@code PUT /api/admin/site/operator} — 운영 주체 값 한 벌(admin-web {@code SiteOperatorRequest}). 검사는 서비스가 칸마다 모아 {@code 400 VALIDATION}
 * ({@code fields.stage} · {@code operatorName} · … · 사업자 칸은 {@code business.businessName} 꼴 · {@code footerNotice} · {@code footerNoticeScope}).
 *
 * @param stage             {@code INDIVIDUAL} · {@code SOLE_PROPRIETOR} · {@code CORPORATION}
 * @param business          사업자 정보 — 사업자 단계에서만 읽는다(개인 단계면 무시하고 비운다)
 * @param footerNotice      푸터 안내 문구(V177) — 한 줄에 한 문장, 줄바꿈으로 잇는다(최대 3줄 · 줄마다 120자). 빈 글자 = 안내 숨김, <b>칸이 없으면(null) 지금 값을 둔다</b>
 * @param footerNoticeScope {@code INDIVIDUAL_ONLY}(개인 단계에서만) · {@code ALWAYS}(늘) — 칸이 없으면(null) 지금 값을 둔다
 */
public record SiteOperatorRequest(String stage, String operatorName, String contactEmail, String phone, Boolean privacyOfficerSameAsOperator,
                                  String privacyOfficerName, String privacyOfficerEmail, String hostingProvider, Business business, String footerNotice,
                                  String footerNoticeScope) {

    /** @param corporateNumber 법인등록번호 — 법인 단계에서만 읽는다 */
    public record Business(String businessName, String representative, String registrationNumber, String address, String mailOrderNumber,
                           String mailOrderAuthority, String corporateNumber) {
    }

    @Override
    public String toString() {
        return "SiteOperatorRequest[stage=" + stage + "]";
    }
}
