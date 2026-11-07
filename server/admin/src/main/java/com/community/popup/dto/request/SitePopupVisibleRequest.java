package com.community.popup.dto.request;

/** {@code PUT /api/admin/site-popups/{id}/visible} — 노출 켜기·끄기만 */
public record SitePopupVisibleRequest(Boolean visible) {
}
