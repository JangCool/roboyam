package com.community.popup.dto.response;

import java.util.List;

/** {@code GET /api/site/popups}(비로그인) — 노출을 켰고 기간 안인 팝업, 목록 차례대로. 사용자별 내용은 없다 */
public record SitePopupsResponse(List<PublicSitePopupView> items) {
}
