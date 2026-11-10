package com.community.sanction.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 반복 검토 상세(admin-web {@code BoardSanctionReviewDetail}) — 목록 줄 칸 + 상세.
 *
 * <p>봉인 검토(줄을 이루는 제재 중 하나라도 「이 글/댓글 작성자」 제재 — V184)면 {@code user} 는 봉인, {@code sanctions}·{@code reviewSanctionIds}·{@code voidedSanctionIds} 는
 * 봉인 제재만(봉인 아닌 제재의 번호·계기 글로 회원이 이어지지 않게), {@code otherBoards} 는 빈 목록. 수({@code recentCount}·{@code recentBoardCount})는 그대로 센다.
 *
 * @param sanctions         이 게시판에서 받은 최근 제재(처리자 포함, 최근 것이 위, 최대 20)
 * @param reviewSanctionIds 이 검토가 센 제재 중 VOIDED 아닌 것 — 「게시판 제재 무효」로 고를 수 있는 것
 * @param siteSanction      결과가 SITE_SANCTIONED 면 부여한 사이트 제재
 * @param voidedSanctionIds 결과가 VOIDED 면 무효로 한 제재
 * @param sealedSource      봉인 검토면 사이트 제재를 걸 대상 글/댓글(가장 최근 봉인 제재의 계기), 아니면 null
 */
public record BoardSanctionReviewDetailView(String id, long no, String status, SanctionUserRefView user, BoardSanctionBoardRefView board, int recentCount,
                                            int recentBoardCount, Instant openedAt, Instant closedAt, String result, List<BoardSanctionLineView> sanctions,
                                            List<String> reviewSanctionIds, List<BoardSanctionOtherBoardView> otherBoards, SanctionActorRefView closedBy, String memo,
                                            BoardSanctionReviewSiteSanctionView siteSanction, List<String> voidedSanctionIds,
                                            BoardSanctionReviewSealedSourceView sealedSource) {
}
