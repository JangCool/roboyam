package com.community.dashboard.dto.response.gadget;

/**
 * 가젯 데이터 묶음 조회({@code GET /api/admin/dashboard/gadgets})의 종류 하나의 값 — 종류별 응답 레코드가 구현한다(admin-web {@code useDashboardGadgetApi.types.ts} 의 모양).
 * 시각은 ISO-8601({@code Instant}), 날짜는 서비스 날짜(한국 시간 {@code YYYY-MM-DD}).
 */
public interface DashboardGadgetData {
}
