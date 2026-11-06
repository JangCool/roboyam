package com.community.board.dto.response;

/**
 * 말머리 하나(admin-web {@code BoardPrefix}).
 *
 * @param common     공통 말머리면 그 종류(QUESTION·REVIEW·CHAT·NOTICE), 고유 말머리면 null
 * @param adultOnly  성인 전용 — 고른 글은 19금(게시판 {@code allowAdult} 일 때만 켤 수 있다)
 * @param usageCount 이 말머리를 쓰는 글 수(삭제·임시저장 포함) — 0 이어야 지울 수 있다
 */
public record BoardPrefixView(String id, String name, String common, boolean staffOnly, boolean adultOnly, String color, boolean active, long usageCount) {
}
