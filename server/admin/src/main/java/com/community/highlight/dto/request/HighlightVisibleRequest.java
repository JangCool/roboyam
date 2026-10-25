package com.community.highlight.dto.request;

/** {@code PUT /api/admin/highlights/{id}/visible} — 노출 켜기·끄기만 */
public record HighlightVisibleRequest(Boolean visible) {
}
