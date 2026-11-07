package com.community.popup.dto.request;

import java.util.List;

/** {@code PUT /api/admin/site-popups/order} — 모든 팝업의 새 차례(지금 팝업 ID 집합과 정확히 같아야 한다) */
public record SitePopupOrderRequest(List<String> ids) {
}
