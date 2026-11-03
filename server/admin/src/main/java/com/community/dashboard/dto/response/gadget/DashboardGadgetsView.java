package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/admin/dashboard/gadgets} 응답 — admin-web {@code DashboardGadgetsResponse}.
 *
 * @param gadgets     요청한 종류마다 값(요청 차례). 권한이 없거나 이 API 대상이 아니거나(TODO·COMMERCE_TODO·MEMO·SHORTCUTS) 서버가 아직 줄 수 없는 종류는 null
 * @param unavailable 권한은 있으나 서버가 아직 데이터를 줄 수 없는 종류(원천 없음 · 2단계) — 화면이 「권한 없음」과 구분하려고 계약에 더한 칸
 */
public record DashboardGadgetsView(Instant generatedAt, Map<String, DashboardGadgetData> gadgets, List<String> unavailable) {
}
