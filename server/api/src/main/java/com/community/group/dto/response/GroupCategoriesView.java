package com.community.group.dto.response;

import java.util.List;

/**
 * {@code GET /api/groups/categories} — 소모임 목록의 칩과 만들기의 분류 고르기.
 *
 * @param total 열린 소모임 전체 수(분류 없는 소모임 포함 — 「전체」 칩)
 * @param items 켜진 소모임 게시판이 하나 이상 할당된 분류만(관리자가 정한 순서)
 */
public record GroupCategoriesView(long total, List<Item> items) {

    /**
     * @param groupCount       그 분류 게시판들(꺼진 게시판 포함)의 열린 소모임 수
     * @param investmentNotice 켜진 게시판 중 하나라도 투자 고지
     * @param boards           그 분류의 켜진 소모임 게시판(할당 순서)
     */
    public record Item(String id, String name, long groupCount, boolean investmentNotice, List<Board> boards) {
    }

    public record Board(String slug, String name, String nameEn, boolean investmentNotice) {
    }
}
