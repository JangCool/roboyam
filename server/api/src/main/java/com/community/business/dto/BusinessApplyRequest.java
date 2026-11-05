package com.community.business.dto;

/**
 * {@code POST /api/business/me}(신청·반려 뒤 재신청) · {@code PUT /api/business/me}(승인 사업자의 수정). 검사는 서비스가 칸 이름별로 모은다(400 {@code fields.*}).
 *
 * @param bizNo               숫자 10자리(하이픈 허용) — 수정·재신청 때 바꾸지 않으면 null(저장된 값 유지)
 * @param phone               수정·재신청 때 바꾸지 않으면 null
 * @param certificateFileId   {@code POST /api/business/files} 가 준 번호(수정 때 지금 사본이면 그 번호) — {@code certificateDelivery = MAIL_FAX} 면 보지 않는다(null)
 * @param openedOn            개업일 {@code YYYY-MM-DD}(필수, 오늘 이후 불가) — 서버가 번호·상호·대표자명과 함께 국세청 진위확인을 부른다(불일치 400 {@code BUSINESS_NTS_MISMATCH})
 * @param certificateDelivery 등록증을 내는 길 {@code UPLOAD}(비우면 기본) · {@code MAIL_FAX}(우편·팩스로 보냄 — 신청·재신청만, 보낼 곳 설정이 비었으면 400). 수정은 파일만
 */
public record BusinessApplyRequest(String companyName, String bizNo, String ownerName, String phone, String email, MyBusinessView.Address address,
                                   String certificateFileId, String agreedTermsVersion, String agreedPrivacyVersion, String openedOn, String certificateDelivery) {

    /** 등록증을 파일로 올리는 신청·수정(V140 전 모양 — 시험·옛 부르는 쪽) */
    public BusinessApplyRequest(String companyName, String bizNo, String ownerName, String phone, String email, MyBusinessView.Address address,
                                String certificateFileId, String agreedTermsVersion, String agreedPrivacyVersion, String openedOn) {
        this(companyName, bizNo, ownerName, phone, email, address, certificateFileId, agreedTermsVersion, agreedPrivacyVersion, openedOn, null);
    }
}
