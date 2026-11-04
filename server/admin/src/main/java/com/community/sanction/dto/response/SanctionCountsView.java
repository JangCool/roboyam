package com.community.sanction.dto.response;

/** 거르기와 무관한 전체 건수 — {@code all} 경고 포함 전부 · {@code active} 진행 중 정지·영구 정지 · {@code closed} 끝남·해제·무효 */
public record SanctionCountsView(long all, long active, long closed) {
}
