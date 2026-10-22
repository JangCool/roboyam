package com.community.menu.dto.response;

/**
 * 게시판 항목이 실어 오는 게시판 요약 — user-web {@code MenuBoardRef}.
 *
 * @param color            표시색(12색 프리셋 이름) 또는 null
 * @param investmentNotice 투자 고지가 붙는 게시판인가
 */
public record MenuBoardView(String id, String slug, String color, boolean investmentNotice) {
}
