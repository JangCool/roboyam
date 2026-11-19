package com.community.board.dto.request;

/**
 * {@code POST /api/admin/boards} — 일반·소모임·핫딜 게시판을 만든다. 검사는 서비스가 칸 경로 키로 모은다(400 {@code VALIDATION} {@code fields.name|slug|description|layout|kind|groupCategoryId|listEmoji}).
 *
 * @param kind            {@code GENERAL}(비우면 이것) · {@code GROUP} · {@code DEAL}(핫딜 — 글쓰기 최소 자격 첫 등급 바로 위, 19금 끔, 인기글 가젯 제외로 만든다).
 *                        {@code PROMO} 등 그 밖은 400 {@code fields.kind}. 만든 뒤 바꿀 수 없다
 * @param groupCategoryId 소모임 분류(소모임 게시판만, 선택) — 일반·핫딜 게시판에 보내거나 없는 분류면 400 {@code fields.groupCategoryId}. 보내려면 {@code group.settings} 도 필요하다
 * @param listEmoji       목록 이모지(선택, V257 — mobile-home-list-contract) — 앞뒤 공백을 떼고 빈 글자·null 이면 없음(게시판 이름 첫 글자), 16자(코드 포인트)를 넘으면 400 {@code fields.listEmoji}
 */
public record BoardCreateRequest(String name, String slug, String description, String layout, String kind, String groupCategoryId, String listEmoji) {

    /** 목록 이모지 칸 없이(없음) */
    public BoardCreateRequest(String name, String slug, String description, String layout, String kind, String groupCategoryId) {
        this(name, slug, description, layout, kind, groupCategoryId, null);
    }
}
