package com.community.post.dto.response;

/**
 * 글이 속한 게시판(글 상세의 {@code board}, 글 줄의 {@code board})
 *
 * @param emoji 게시판 목록 이모지(V257 — mobile-home-list-contract), 없으면 null(화면이 게시판 이름 첫 글자를 쓴다)
 */
public record PostBoardRefView(String id, String slug, String name, String nameEn, String color, String emoji) {
}
