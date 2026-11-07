package com.community.popup.dto.request;

/**
 * {@code PUT /api/admin/site-popups/layout} — 여러 창 배치(사이트 전체 하나).
 *
 * @param layout {@code HORIZONTAL}(PC 에서 가로로 나란히) · {@code VERTICAL}(위아래로 쌓기)
 */
public record SitePopupLayoutRequest(String layout) {
}
