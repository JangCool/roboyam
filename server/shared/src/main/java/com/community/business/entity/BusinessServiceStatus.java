package com.community.business.entity;

/** 서비스별 이용 권한 상태 — 신원 상태를 따라가고, 한 서비스만 정지할 수도 있다(2026-11-02 — 줄의 「서비스만 정지」 표시). {@code OFF} 는 탈퇴 등으로 닫힌 줄 */
public enum BusinessServiceStatus {
    PENDING, APPROVED, REJECTED, SUSPENDED, OFF
}
