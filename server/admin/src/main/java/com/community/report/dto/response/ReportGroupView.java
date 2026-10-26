package com.community.report.dto.response;

import com.community.group.dto.response.GroupCategoryRefView;
import com.community.group.dto.response.GroupPersonView;
import java.time.Instant;

/**
 * 소모임 사건(V95)의 대상 소모임 — 지금 값(신고 당시 이름·소개는 상세의 {@code content.snapshot}). 소모임 행이 없으면 상세의 {@code group} 이 null.
 *
 * @param status        ACTIVE · INACTIVE · DELETED(해산) 등 소모임 상태 그대로
 * @param visibility    PUBLIC · PRIVATE
 * @param joinType      OPEN · APPROVAL
 * @param stockNotice   투자 고지 게시판에서 추가 동의를 받은 소모임
 * @param category      분류(분류에서 빠진 게시판이면 null)
 * @param leader        지금 모임장(신고 당시 모임장은 사건의 {@code author})
 * @param deactivatedAt 지금 비활성이면 그 시각, 아니면 null
 */
public record ReportGroupView(String id, String name, String description, String status, String visibility, String joinType, int memberCount, int capacity,
                              boolean offline, boolean stockNotice, GroupCategoryRefView category, GroupPersonView leader, Instant openedAt,
                              Instant deactivatedAt) {
}
