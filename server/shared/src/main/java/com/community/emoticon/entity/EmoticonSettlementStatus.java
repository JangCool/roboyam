package com.community.emoticon.entity;

/**
 * 작가 월 정산 상태(V145 {@code ck_emoticon_settlements_status} · V148 — 두 앱 목업 {@code EmoticonSettlementStatus}).
 * {@code ESTIMATED}(예정 — 금액을 조회 때 계산) → {@code CONFIRMED}(확정 — 금액을 굳힘) → {@code PAID}(지급 완료 기록) · 예정·확정 → {@code HELD}(보류 — 사유) → 보류 전 상태 ·
 * 예정·보류(굳히지 않은 줄) → {@code WAIVED}(포기 — 정산 정보 없이 탈퇴한 작가의 남은 수익: 탈퇴 때 회원 동의 또는 운영자 포기 처리. 지급액 0 으로 굳히고 더 바뀌지 않는다,
 * 그 줄의 양수 넘긴 금액은 다음 달로 이어지지 않는다).
 */
public enum EmoticonSettlementStatus {
    ESTIMATED,
    CONFIRMED,
    PAID,
    HELD,
    WAIVED
}
