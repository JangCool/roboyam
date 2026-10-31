package com.community.business.entity;

/**
 * 사업자 신원 상태(business-contract 「모델」) — 심사 중 → 승인·반려(재신청 = 같은 줄 심사 중) · 승인 ↔ 정지 · 모든 상태 → 탈퇴.
 * 승인 뒤 상호·번호를 바꾸면 심사 중 + {@code re_review}(F7).
 */
public enum BusinessStatus {
    PENDING, APPROVED, REJECTED, SUSPENDED, WITHDRAWN;

    /** 등록번호 중복을 따지는 「살아 있는 줄」 — 부분 유일 색인 {@code ux_business_profiles_biz_no_live} 와 같은 목록 */
    public boolean live() {
        return this == PENDING || this == APPROVED || this == SUSPENDED;
    }
}
