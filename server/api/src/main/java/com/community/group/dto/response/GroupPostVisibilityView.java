package com.community.group.dto.response;

/** {@code PUT /api/groups/{id}/posts/{postId}/visibility} — 바뀐 뒤(또는 이미 그 값인) 공개 상태 PUBLIC · PRIVATE */
public record GroupPostVisibilityView(String visibility) {
}
