package com.community.business.entity;

/**
 * 국세청 상태조회 결과(B1) — 계속사업자 · 휴업 · 폐업 · 국세청에 없는 번호 · 조회 불가(장애·키 없음). 계속사업자만 그냥 승인할 수 있고,
 * 조회 불가는 사유를 받아 승인할 수 있다(감사). 휴·폐업·없는 번호는 승인할 수 없다(409 {@code BUSINESS_NTS_NOT_ACTIVE}).
 */
public enum NtsBizStatus {
    CONTINUING, PAUSED, CLOSED, NOT_FOUND, UNAVAILABLE
}
