package com.community.point.dto.response;

import java.util.List;

/** {@code GET /api/admin/points/ledger} — admin-web {@code PointLedgerPage}. {@code total} 은 조건에 맞는 전체 줄 수 */
public record PointLedgerPageView(List<PointLedgerItemView> items, long total, PointLedgerSummaryView summary) {
}
