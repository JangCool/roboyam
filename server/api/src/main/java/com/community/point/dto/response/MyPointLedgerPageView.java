package com.community.point.dto.response;

import java.util.List;

/** {@code GET /api/me/points/ledger} — 최신이 위, {@code nextCursor} 를 다음 요청의 {@code cursor} 로 */
public record MyPointLedgerPageView(List<MyPointLedgerItemView> items, boolean hasNext, String nextCursor) {
}
