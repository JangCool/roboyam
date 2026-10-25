package com.community.post.dto.response;

import java.util.List;

/** {@code GET /api/admin/posts/board-options} */
public record AdminPostBoardOptionsView(List<AdminPostBoardOptionView> boards) {
}
