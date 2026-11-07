package com.community.tips.dto.response;

import com.community.post.dto.response.PostBoardRefView;
import java.util.List;

/**
 * {@code GET /api/tips?board=} — 묶음 머리 몫(user-web {@code TipsOverview}, 태그 칩 {@code tags} 는 서버에 없어 뺀다).
 *
 * @param boards     묶음에 든 켜진 게시판(게시판 차례 — 칩 순서). {@code board} 로 하나를 골라도 묶음 전체
 * @param headline   「이번 주 추천」 최대 5개 — {@code tips} 곳 하이라이트(관리자 순서) 먼저, 모자라면 최근 7일 추천순, 그래도 모자라면 더 오래된 글 추천순. 첫 글이 큰 카드
 * @param mostViewed 많이 본 글 최대 5개 — 최근 30일 조회순, 모자라면 더 오래된 글 조회순
 */
public record TipsOverviewView(List<PostBoardRefView> boards, List<TipsPostView> headline, List<TipsPostView> mostViewed) {
}
