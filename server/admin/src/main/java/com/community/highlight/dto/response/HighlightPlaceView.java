package com.community.highlight.dto.response;

/**
 * {@code GET /api/admin/highlights/places} 의 한 줄 — 하이라이트를 놓을 수 있는 곳.
 *
 * @param key      곳 키({@code home} · {@code tips} · 가젯 페이지 키 · 게시판 곳 {@code board-{boardId}})
 * @param name     보이는 이름(요청 언어 — 홈 · 팁·강좌·사용기 · 가젯 페이지 이름 · 게시판 이름)
 * @param kind     {@code HOME} · {@code TIPS} · {@code PAGE}(가젯 페이지) · {@code BOARD}(게시판 곳 — 고른 글이 있거나 자동 채우기를 끈 게시판만, board-recommend-contract)
 * @param total    그 곳의 하이라이트 수(노출 여부와 상관없이 — 왼쪽 곳 메뉴에 늘 보인다)
 * @param boardId  게시판 곳이면 게시판 ID(그 밖은 null)
 * @param autoFill 게시판 곳이면 「모자라면 추천 많은 글로 자동 채우기」(그 밖은 null)
 */
public record HighlightPlaceView(String key, String name, String kind, long total, String boardId, Boolean autoFill) {

    /** 게시판 곳이 아닌 곳 */
    public HighlightPlaceView(String key, String name, String kind, long total) {
        this(key, name, kind, total, null, null);
    }
}
