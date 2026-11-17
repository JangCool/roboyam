package com.community.title.dto.response;

import java.util.List;

/** 게시판 매니저의 칭호 신청 목록({@code GET /api/boards/{slug}/staff/title-applications} — user-web {@code BoardTitleApplicationPage}) — 최신 순 200개까지 */
public record BoardTitleApplicationPageView(List<BoardTitleApplicationView> items) {
}
