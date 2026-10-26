package com.community.group.dto.response;

import com.community.dm.dto.response.DmPartnerView;
import java.time.Instant;
import java.util.List;

/** {@code GET /api/groups/{id}/events/{eventId}/attendees} — 참석(GOING)으로 응답한 지금 멤버, 응답이 최근인 순. 쪽 번호 1부터, {@code total} 은 참석 인원 */
public record GroupEventAttendeePageView(List<Item> items, int page, boolean hasNext, long total) {

    /**
     * @param user        회원(닉네임·등급 — 장식·아바타는 싣지 않는다, 원본과 같다)
     * @param respondedAt 응답 시각
     * @param leader      모임장인가
     */
    public record Item(DmPartnerView user, Instant respondedAt, boolean leader) {
    }
}
