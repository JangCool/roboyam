package com.community.highlight.dto.response;

/**
 * 게시판 추천 글 카드 한 장(board-recommend-contract 「화면」).
 *
 * @param postId    글 ID
 * @param title     큰 글자 — 고른 글은 하이라이트 「노출 제목」(없으면 글 제목), 자동 채운 글은 글 제목
 * @param label     고른 글의 하이라이트 「요약」을 한 줄로(줄바꿈·연속 공백을 한 칸으로). 비었거나 자동 채운 글이면 null — 화면이 「운영자 추천」·「추천 n · 말머리」로 채운다
 * @param kind      {@code PICKED}(운영자가 고른 글) · {@code AUTO}(추천 많은 글로 채운 글)
 * @param likeCount 추천 수(캐시 시각 기준 — 조금 늦을 수 있다)
 * @param prefix    말머리 이름(없으면 null)
 */
public record BoardRecommendItemView(String postId, String title, String label, String kind, int likeCount, String prefix) {

    public static final String PICKED = "PICKED";
    public static final String AUTO = "AUTO";
}
