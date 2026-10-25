package com.community.dm.dto.response;

import java.util.List;

/** {@code GET /api/dm/recipients?q=} — 최대 10명 */
public record DmRecipientListView(List<DmRecipientView> items) {
}
