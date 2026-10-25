package com.community.post.dto.request;

import java.util.List;

/**
 * {@code POST /api/admin/posts/bulk} — {@code action} HIDE · UNHIDE · DELETE, {@code ids} 1~50개, {@code reason} 1~200자,
 * {@code notifyAuthor}(HIDE 만 — 작성 회원에게 POST_HIDDEN 알림).
 */
public record AdminPostBulkRequest(String action, List<String> ids, String reason, Boolean notifyAuthor) {
}
