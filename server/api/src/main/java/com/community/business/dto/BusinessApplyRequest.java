package com.community.business.dto;

/**
 * {@code POST /api/business/me}(신청·반려 뒤 재신청) · {@code PUT /api/business/me}(승인 사업자의 수정). 검사는 서비스가 칸 이름별로 모은다(400 {@code fields.*}).
 *
 * @param bizNo             숫자 10자리(하이픈 허용) — 수정·재신청 때 바꾸지 않으면 null(저장된 값 유지)
 * @param phone             수정·재신청 때 바꾸지 않으면 null
 * @param certificateFileId {@code POST /api/business/files} 가 준 번호(수정 때 지금 사본이면 그 번호)
 */
public record BusinessApplyRequest(String companyName, String bizNo, String ownerName, String phone, String email, MyBusinessView.Address address,
                                   String certificateFileId, String agreedTermsVersion, String agreedPrivacyVersion) {
}
