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
     * @param newPostCount 내가 글 탭을 마지막으로 본 뒤(본 적이 없으면 가입 뒤) 올라온 남의 보이는 글 수(2단계)
     * @param nextEvent    그 소모임의 가장 가까운 시작 전(UPCOMING) 일정(4단계), 없으면 null
     */
    public record Joined(@JsonUnwrapped GroupRowView group, String role, Instant joinedAt, int newPostCount, NextEvent nextEvent) {
    }

    /** 내 소모임 한 줄의 다음 일정(community {@code NextEvent}) — 취소·시작한 일정은 빠진다 */
    public record NextEvent(String id, String title, Instant startsAt) {
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
