package com.community.group.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/me/groups} — 내 소모임.
 *
 * {@code joined}·{@code applications} 줄은 {@code GroupRow} 칸을 펼친 것에 칸을 더한다(community 와 같은 모양).
 *
 * @param joined       가입한 열린 소모임(가입이 최근인 순)
 * @param applications 승인 대기 가입 신청(열린 소모임)
 * @param requests     내 개설 신청 중 신청 중·반려(최근이 위 — 승인되면 joined 로 간다)
 */
public record MyGroupsView(List<Joined> joined, List<Application> applications, List<Request> requests) {

    /**
     * @param role         LEADER · MANAGER · MEMBER
     * @param newPostCount 새 글 수(2단계에서 채운다 — 지금 늘 0)
     * @param nextEvent    다음 일정(4단계 — 지금 늘 null)
     */
    public record Joined(@JsonUnwrapped GroupRowView group, String role, Instant joinedAt, int newPostCount, Object nextEvent) {
    }

    public record Application(@JsonUnwrapped GroupRowView group, Instant appliedAt) {
    }

    /**
     * @param board        신청이 쓴 게시판(반려된 신청을 다시 낼 때 폼이 되살린다)
     * @param status       PENDING · REJECTED
     * @param rejectReason 반려 사유(반려일 때)
     */
    public record Request(String id, String name, GroupCategoryRefView category, GroupBoardRefView board, String joinType, int capacity, String description,
                          boolean offline, String visibility, String status, String rejectReason, Instant requestedAt, Instant reviewedAt) {
    }
}
