package com.community.post.dto.response;

import java.util.List;

/** {@code GET /api/admin/posts} — {@code page} 는 0 부터 */
public record AdminPostListView(long total, int page, int size, List<AdminPostItemView> items) {
}
