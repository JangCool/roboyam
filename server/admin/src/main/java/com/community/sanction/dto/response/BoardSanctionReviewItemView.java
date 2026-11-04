package com.community.sanction.dto.response;

import java.time.Instant;

/**
 * 반복 검토 한 줄(admin-web {@code BoardSanctionReviewItem}).
 *
 * @param recentCount      열린 건은 지금 기준, 닫힌 건은 닫은 때 기준 — 이 게시판에서 최근 7일 안 VOIDED 아닌 제재 수
 * @param recentBoardCount 같은 기준으로 이 회원이 게시판 제재를 받은 게시판 수
 */
public record BoardSanctionReviewItemView(String id, long no, String status, SanctionUserRefView user, BoardSanctionBoardRefView board, int recentCount,
                                          int recentBoardCount, Instant openedAt, Instant closedAt, String result) {
}
