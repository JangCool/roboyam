package com.community.dm.dto.response;

import java.util.List;

/** {@code GET /api/dm/threads} — 마지막 쪽지가 최근인 대화가 위 */
public record DmThreadListView(List<DmThreadItemView> items, boolean hasNext, String nextCursor) {
}
