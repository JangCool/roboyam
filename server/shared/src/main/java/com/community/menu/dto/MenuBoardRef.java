package com.community.menu.dto;

/**
 * 메뉴가 보는 게시판 요약 — 대상 확인·이름·주소·공개 응답의 {@code board} 칸 재료. 꺼진 게시판도 담는다(관리자 상태 {@code BOARD_INACTIVE}).
 *
 * @param color            표시색(12색 프리셋 이름) 또는 null
 * @param investmentNotice 투자 고지 게시판인가
 */
public record MenuBoardRef(long id, String slug, String name, String nameEn, String color, boolean investmentNotice, boolean active) {
}
