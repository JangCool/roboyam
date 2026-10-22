package com.community.board.dto.response;

import com.community.board.dto.BoardDefaultsValues;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET·PUT /api/admin/boards/defaults}(admin-web {@code BoardDefaultsResponse}).
 *
 * @param followingCount 정책을 하나도 따로 정하지 않은 게시판 수
 * @param overrides      칸마다 그 칸을 따로 정한 게시판들 — 없으면 칸이 빠진다
 * @param updatedBy      마지막으로 저장한 운영자 이름 — 저장한 적 없거나 운영자가 지워졌으면 null
 */
public record BoardDefaultsResponse(BoardDefaultsValues defaults, int boardCount, int followingCount, Map<String, List<BoardRefView>> overrides,
                                    Instant updatedAt, String updatedBy) {
}
