package com.community.group.dto.request;

/**
 * {@code PUT /api/groups/{id}/posts/{postId}/visibility} — 공개 전환·되돌리기(community api-groups.md 8절).
 *
 * @param visibility   PUBLIC(공개로 전환 — 작성자만) · PRIVATE(비공개로 되돌리기 — 작성자 · 모임장 · 되돌리기를 맡은 매니저)
 * @param acknowledged 공개로 전환할 때 true 여야 한다 — 「닉네임과 프로필이 함께 공개되고 다른 멤버의 개인정보가 없는지 확인했다」 동의
 */
public record GroupPostVisibilityRequest(String visibility, Boolean acknowledged) {
}
