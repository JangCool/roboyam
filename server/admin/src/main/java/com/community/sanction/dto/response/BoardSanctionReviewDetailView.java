package com.community.sanction.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 반복 검토 상세(admin-web {@code BoardSanctionReviewDetail}) — 목록 줄 칸 + 상세.
 *
 * @param sanctions         이 게시판에서 받은 최근 제재(처리자 포함, 최근 것이 위, 최대 20)
 * @param reviewSanctionIds 이 검토가 센 제재 중 VOIDED 아닌 것 — 「게시판 제재 무효」로 고를 수 있는 것
 * @param siteSanction      결과가 SITE_SANCTIONED 면 부여한 사이트 제재
 * @param voidedSanctionIds 결과가 VOIDED 면 무효로 한 제재
 */
public record BoardSanctionReviewDetailView(String id, long no, String status, SanctionUserRefView user, BoardSanctionBoardRefView board, int recentCount,
                                            int recentBoardCount, Instant openedAt, Instant closedAt, String result, List<BoardSanctionLineView> sanctions,
                                            List<String> reviewSanctionIds, List<BoardSanctionOtherBoardView> otherBoards, SanctionActorRefView closedBy, String memo,
                                            BoardSanctionReviewSiteSanctionView siteSanction, List<String> voidedSanctionIds) {
}
