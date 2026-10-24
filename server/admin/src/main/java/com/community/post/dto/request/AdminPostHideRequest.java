package com.community.post.dto.request;

/** {@code POST /api/admin/posts/{id}/hide} — {@code reason} 1~200자 필수, {@code notifyAuthor} 면 작성 회원에게 POST_HIDDEN 알림(사유 포함, 기본 false) */
public record AdminPostHideRequest(String reason, Boolean notifyAuthor) {
}
