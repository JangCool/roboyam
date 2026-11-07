package com.community.terms.dto;

import com.community.site.dto.SiteOperatorValues;
import tools.jackson.databind.json.JsonMapper;

/**
 * 게시 당시 운영 주체(게시본 {@code terms_versions.operator_snapshot} JSON) — 게시할 때 자리표시를 바꾼 값 그대로. 나중에 단계를 바꿔도 지난 판의 사본은 바뀌지 않는다.
 * 관리자 응답에는 {@code stage}·{@code operatorName}·{@code businessName} 만 싣는다(나머지는 기록으로 남긴다). 빈 선택 칸은 null.
 *
 * @param stage          단계 이름({@code SiteOperatorStage})
 * @param businessName   사업자 단계의 상호·회사명(개인은 null)
 * @param privacyOfficer 개인정보 보호책임자 이름(「같음」이면 운영자 이름)
 */
public record TermsOperatorSnapshot(String stage, String operatorName, String contactEmail, String phone, String privacyOfficer, String privacyOfficerEmail,
                                    String hostingProvider, String businessName, String representative, String registrationNumber, String corporateNumber,
                                    String address, String mailOrderNumber, String mailOrderAuthority, String serviceName) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 운영 주체 값 → 사본 */
    public static TermsOperatorSnapshot of(SiteOperatorValues v, String serviceName) {
        SiteOperatorValues.Business b = v.business();
        String officerEmail = v.privacyOfficerSameAsOperator() ? v.contactEmail() : v.privacyOfficerEmail();
        return new TermsOperatorSnapshot(v.stage().name(), v.operatorName(), blank(v.contactEmail()), v.phone(), blank(v.privacyOfficer()), blank(officerEmail),
                v.hostingProvider(), b == null ? null : blank(b.businessName()), b == null ? null : blank(b.representative()),
                b == null ? null : blank(b.registrationNumber()), b == null ? null : b.corporateNumber(), b == null ? null : blank(b.address()),
                b == null ? null : b.mailOrderNumber(), b == null ? null : b.mailOrderAuthority(), blank(serviceName));
    }

    /** JSON 글자 */
    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    /** JSON 글자 → 사본. 없거나 읽을 수 없으면 null(옛 판·모양이 다른 판에서 조회가 멈추면 안 된다) */
    public static TermsOperatorSnapshot parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, TermsOperatorSnapshot.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public String toString() {
        return "TermsOperatorSnapshot[stage=" + stage + "]";
    }
}
