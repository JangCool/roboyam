package com.community.post.dto.request;

/** {@code POST /api/admin/posts/{id}/move} — 옮길 게시판(켜진 게시판) · 말머리(그 게시판의 켜진 말머리, 없으면 null) · 사유 1~200자 */
public record AdminPostMoveRequest(String boardId, String prefixId, String reason) {
}
