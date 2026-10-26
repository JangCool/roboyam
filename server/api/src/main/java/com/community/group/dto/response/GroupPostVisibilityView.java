package com.community.group.dto.response;

/**
 * {@code PUT /api/groups/{id}/posts/{postId}/visibility}·{@code POST …/publish-request/approve} — 바뀐 뒤(또는 이미 그 값인) 공개 상태(community api-groups.md 11-6).
 *
 * @param visibility  PUBLIC(비멤버에게 보임) · PRIVATE
 * @param publicBy    GROUP(공개 소모임이라서) · APPROVED(공개 요청 승인) · DIRECT(직접 공개) · null(비멤버에게 안 보임)
 * @param membersOnly 공개 소모임에서 멤버 전용으로 돌려 둔 상태
 */
public record GroupPostVisibilityView(String visibility, String publicBy, boolean membersOnly) {
}
