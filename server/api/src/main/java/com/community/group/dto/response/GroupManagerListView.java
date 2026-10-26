package com.community.group.dto.response;

import com.community.dm.dto.response.DmPartnerView;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/groups/{id}/managers} — 매니저 목록(멤버만, community api-groups.md 5-1). 임명이 이른 순(같으면 회원 ID).
 *
 * @param count    지금 매니저 수(= {@code items} 길이)
 * @param limit    유효 상한 = min(소모임에 정해 둔 상한, 매니저 규칙의 최대 인원)
 * @param limitMax 매니저 규칙의 최대 인원(모임장이 정할 수 있는 최댓값)
 */
public record GroupManagerListView(List<Item> items, int count, int limit, int limitMax) {

    /**
     * @param user        멤버 탭과 같은 사람 칸(탈퇴했으면 {@code withdrawn: true})
     * @param appointedAt 임명 시각(맡은 일을 바꿔도 그대로)
     * @param permissions 맡은 일 키(고정 순서) — 보는 사람이 모임장이거나 그 매니저 본인일 때만, 그 밖 null
     */
    public record Item(DmPartnerView user, Instant appointedAt, List<String> permissions) {
    }
}
