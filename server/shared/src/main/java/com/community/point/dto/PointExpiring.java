package com.community.point.dto;

/**
 * 가장 가까운 소멸 예정 — 회원 포인트 요약({@code GET /api/me/points})과 관리자 회원 묶음({@code GET /api/admin/members/{id}/point-lots})이 같이 쓴다(user-web·admin-web {@code PointExpiring}).
 *
 * @param date   소멸일 — 한국 시간 서비스 날짜 {@code YYYY-MM-DD}(소멸 시각을 KST 로 바꾼 날짜)
 * @param amount 그 날짜에 소멸할 무상 포인트의 합(그날 소멸일인 남은 묶음 전부, 지금 이미 지난 것은 빼고)
 */
public record PointExpiring(String date, long amount) {
}
