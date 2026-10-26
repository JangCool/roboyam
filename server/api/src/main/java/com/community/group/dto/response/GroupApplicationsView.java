package com.community.group.dto.response;

import com.community.dm.dto.response.DmPartnerView;
import java.time.Instant;
import java.util.List;

/** {@code GET /api/groups/{id}/applications} — 승인 대기 가입 신청(오래된 신청이 위) + 정원·멤버 수 */
public record GroupApplicationsView(List<Item> items, int capacity, int memberCount) {

    /** @param kickedBefore 이 소모임에서 강퇴된 적이 있다 */
    public record Item(DmPartnerView user, String message, Instant appliedAt, boolean kickedBefore) {
    }
}
