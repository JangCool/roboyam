package com.community.menu.dto;

/**
 * 메뉴가 보는 게시판 요약 — 대상 확인·이름·주소·공개 응답의 {@code board} 칸 재료. 꺼진 게시판도 담는다(관리자 상태 {@code BOARD_INACTIVE}).
 *
 * @param color            표시색(12색 프리셋 이름) 또는 null
 * @param investmentNotice 투자 고지 게시판인가
 * @param adultOnly        성인 전용 게시판인가(V226 — adult-board-contract) — 공개 메뉴는 성인이 아닌 사람에게서 이 항목을 뺀다({@code BoardMenuTargetResolver})
 */
public record MenuBoardRef(long id, String slug, String name, String nameEn, String color, boolean investmentNotice, boolean active, boolean adultOnly) {

    /** 성인 전용이 아닌 게시판 */
    public MenuBoardRef(long id, String slug, String name, String nameEn, String color, boolean investmentNotice, boolean active) {
        this(id, slug, name, nameEn, color, investmentNotice, active, false);
    }
}
