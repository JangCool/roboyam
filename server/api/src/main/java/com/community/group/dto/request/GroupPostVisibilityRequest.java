package com.community.group.dto.request;

/**
 * {@code PUT /api/groups/{id}/posts/{postId}/visibility} — 한 경로 네 가지 일(community api-groups.md 11-6 — 8절 개정): 비공개 소모임의 직접 공개·되돌리기,
 * 공개 소모임의 멤버 전용으로 돌리기·다시 공개({@code GroupPostVisibilityService#setPostVisibility}).
 *
 * @param visibility   PUBLIC · PRIVATE
 * @param acknowledged 비멤버에게 보이게 바꿀 때(직접 공개 · 다시 공개 · 기능이 꺼져 있을 때 작성자의 공개 전환) true 여야 한다 —
 *                     「닉네임과 프로필이 함께 공개되고 다른 멤버의 개인정보가 없는지 확인했다」 동의
 */
public record GroupPostVisibilityRequest(String visibility, Boolean acknowledged) {
}
