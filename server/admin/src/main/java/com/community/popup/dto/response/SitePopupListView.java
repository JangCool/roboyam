package com.community.popup.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/site-popups}(·{@code PUT /order}) — 모든 팝업(차례대로).
 *
 * @param total        전체 수
 * @param showingCount 지금 노출 중(SHOWING) 수
 * @param layout       여러 창 배치 {@code HORIZONTAL} · {@code VERTICAL}(사이트 전체 하나)
 */
public record SitePopupListView(List<SitePopupItemView> items, int total, int showingCount, String layout) {
}
