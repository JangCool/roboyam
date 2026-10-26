package com.community.group.dto.response;

import com.community.dm.dto.response.DmPartnerView;
import java.time.Instant;
import java.util.List;

/** {@code GET /api/groups/{id}/members} — 모임장 → 매니저 → 멤버(같은 역할 안에서는 가입이 이른 순), 쪽 번호 1부터 */
public record GroupMemberPageView(List<Item> items, int page, boolean hasNext, long total) {

    /**
     * @param role               LEADER · MANAGER · MEMBER
     * @param managerPermissions 매니저 줄이고 보는 사람이 모임장이거나 그 매니저 본인일 때만 맡은 일 키(고정 순서), 그 밖 null
     */
    public record Item(DmPartnerView user, String role, Instant joinedAt, List<String> managerPermissions) {
    }
}
