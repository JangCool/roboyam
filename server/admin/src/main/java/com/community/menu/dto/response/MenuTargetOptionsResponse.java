package com.community.menu.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/menu/target-options?set=} — admin-web {@code MenuTargetOptions}. 대상 고르기·상태 계산·미리보기에 쓰는 값 한 번에.
 *
 * @param boards       모든 게시판(꺼진 것 포함, 게시판 순서)
 * @param grades       등급(서열 순서)
 * @param salesEnabled 공동구매 판매 스위치 — 지금은 늘 true({@code MenuSales})
 */
public record MenuTargetOptionsResponse(List<MenuBoardOptionView> boards, List<MenuGradeOptionView> grades, boolean salesEnabled) {
}
