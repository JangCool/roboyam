package com.community.dm.dto.response;

import java.util.List;

/** {@code GET /api/dm/threads/{id}} — {@code items} 는 최근 쪽지가 앞, {@code hasNext} 는 더 오래된 쪽 */
public record DmRoomView(DmThreadHeaderView thread, List<DmMessageView> items, boolean hasNext, String nextCursor, DmQuotaView quota) {
}
