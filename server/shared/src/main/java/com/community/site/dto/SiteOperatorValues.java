package com.community.site.dto;

import com.community.site.entity.SiteOperatorStage;

/**
 * 운영 주체 값 한 벌(admin-web {@code SiteOperatorProfile} 과 같은 모양) — 빈 선택 칸은 null. 개인 단계는 {@code business} 가 null 이다.
 *
 * @param operatorName                 운영자 이름(1~30자, 저장한 적 없으면 빈 글자)
 * @param contactEmail                 연락 이메일(저장한 적 없으면 빈 글자)
 * @param privacyOfficerSameAsOperator 개인정보 보호책임자 = 운영자(참이면 아래 두 칸은 null)
 */
public record SiteOperatorValues(SiteOperatorStage stage, String operatorName, String contactEmail, String phone, boolean privacyOfficerSameAsOperator,
                                 String privacyOfficerName, String privacyOfficerEmail, String hostingProvider, Business business) {

    /** 저장한 적 없는 처음 값 — 개인 단계, 빈 칸 */
    public static final SiteOperatorValues INITIAL = new SiteOperatorValues(SiteOperatorStage.INDIVIDUAL, "", "", null, true, null, null, null, null);

    /**
     * 사업자 정보 — 개인사업자·법인일 때만.
     *
     * @param businessName       상호(법인은 회사명)
     * @param registrationNumber 사업자등록번호 {@code 123-45-67890}
     * @param mailOrderNumber    통신판매업 신고번호(없으면 null — 공구·충전·상점 판매를 켤 수 없다)
     * @param corporateNumber    법인등록번호(법인만, 그 밖에는 null)
     */
    public record Business(String businessName, String representative, String registrationNumber, String address, String mailOrderNumber,
                           String mailOrderAuthority, String corporateNumber) {
    }

    /** 개인정보 보호책임자 이름 — 「운영자와 같음」이면 운영자 이름 */
    public String privacyOfficer() {
        return privacyOfficerSameAsOperator ? operatorName : privacyOfficerName;
    }

    /** 통신판매업 신고번호(개인이거나 없으면 null) */
    public String mailOrderNumber() {
        return business == null ? null : business.mailOrderNumber();
    }

    @Override
    public String toString() {
        return "SiteOperatorValues[stage=" + stage + ", business=" + (business != null) + "]";
    }
}
