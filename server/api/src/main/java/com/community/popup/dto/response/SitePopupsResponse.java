package com.community.popup.dto.response;

import java.util.List;

/**
 * {@code GET /api/site/popups}(비로그인) — 노출을 켰고 기간 안인 팝업, 목록 차례대로. 사용자별 내용은 없다.
 *
 * @param layout 여러 창 배치 {@code HORIZONTAL}(PC 가로로 나란히) · {@code VERTICAL}(위아래로 쌓기) — 모바일은 어느 쪽이든 창을 하나씩 차례로
 */
public record SitePopupsResponse(String layout, List<PublicSitePopupView> items) {
}
