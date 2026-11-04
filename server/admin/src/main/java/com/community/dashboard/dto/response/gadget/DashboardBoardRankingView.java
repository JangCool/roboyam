package com.community.dashboard.dto.response.gadget;

import java.util.List;

/** {@code BOARD_RANKING} — 오늘 활발한 공개 게시판 상위 5(글 + 댓글 많은 차례, 게시판 활동 일별 집계). 이름은 게시판 사본(지금 이름) */
public record DashboardBoardRankingView(List<Item> items) implements DashboardGadgetData {

    public record Item(String boardId, String boardName, long posts, long comments) {
    }
}
