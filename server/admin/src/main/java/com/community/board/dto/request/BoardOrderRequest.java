package com.community.board.dto.request;

import java.util.List;

/** {@code PUT /api/admin/boards/order} — 모든 게시판 ID 의 새 순서(집합이 지금 게시판과 같아야 한다) */
public record BoardOrderRequest(List<String> ids) {
}
