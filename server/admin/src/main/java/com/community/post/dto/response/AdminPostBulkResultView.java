package com.community.post.dto.response;

import java.util.List;

/** {@code POST /api/admin/posts/bulk} — 글마다 따로 처리한 결과(요청 차례, 같은 ID 는 한 번만) */
public record AdminPostBulkResultView(List<String> done, List<AdminPostBulkFailureView> failed) {
}
