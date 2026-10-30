package com.community.business.entity;

/** 서비스별 이용 권한 상태 — 지금은 신원 상태를 따라간다(서비스별 따로 처리는 후속). {@code OFF} 는 탈퇴 등으로 닫힌 줄 */
public enum BusinessServiceStatus {
    PENDING, APPROVED, REJECTED, SUSPENDED, OFF
}
