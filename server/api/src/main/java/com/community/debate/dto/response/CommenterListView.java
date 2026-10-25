package com.community.debate.dto.response;

import java.util.List;

/** {@code GET /api/posts/{postId}/commenters?q=} — 최대 10명, 가장 최근에 쓴 회원 먼저 */
public record CommenterListView(List<CommenterView> items) {
}
