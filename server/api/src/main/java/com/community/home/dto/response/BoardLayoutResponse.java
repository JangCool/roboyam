package com.community.home.dto.response;

import com.community.home.dto.HomeGridLayout;
import java.util.List;

/**
 * {@code GET /api/boards/{slug}/layout} — user-web {@code BoardLayoutResponse}(홈 가젯 응답 모양 + 적용 화면, board-layout-contract). 본문 가젯({@code BOARD_BODY})은
 * {@code data: null}·{@code empty: false}, 대상 게시판 {@code BOARD} 가젯은 이 게시판의 데이터다.
 *
 * @param theme   이 배치가 어느 테마의 것인가
 * @param screens 적용 화면 — 끈 화면(게시판 목록 {@code list} · 글 보기 {@code post})은 화면이 본문만 그린다
 */
public record BoardLayoutResponse(String theme, Screens screens, HomeGridLayout layout, List<HomeGadgetView> gadgets) {

    public record Screens(boolean list, boolean post) {
    }
}
