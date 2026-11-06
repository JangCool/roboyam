package com.community.board.dto;

/**
 * 말머리 하나의 읽기 사본({@link com.community.board.service.BoardCatalog}). 꺼진 말머리도 담는다 — 예전 글의 말머리를 그릴 때 쓴다(고르기 목록에는 켜진 것만).
 *
 * @param commonKey 공통 말머리(QUESTION·REVIEW·CHAT·NOTICE)면 그 종류, 고유 말머리면 null
 * @param adultOnly 성인 전용 — 이 말머리를 고른 글은 19금으로 저장한다(V152)
 */
public record BoardPrefixInfo(long id, String name, boolean staffOnly, boolean adultOnly, String commonKey, String color, boolean active, int sortOrder) {

    public boolean common() {
        return commonKey != null;
    }
}
