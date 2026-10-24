package com.community.post.dto;

/**
 * 게시글 관리 목록의 거르기(서비스가 요청 값을 풀어 만든다).
 *
 * @param postId   글 번호로 찾기(검색어가 {@code #번호}·15~19자리 숫자) — 아니면 null
 * @param text     제목·본문·작성자 닉네임(비회원 이름) 부분 일치(2자 이상) — 아니면 null
 * @param boardId  게시판 — null 이면 전체
 * @param prefixId 말머리(게시판을 고른 뒤) — null 이면 전체
 * @param state    PUBLIC · HIDDEN · DELETED — null 이면 전체(ALL)
 * @param oldest   오래된순(아니면 최신순)
 * @param none     아무 글도 맞지 않는 거르기(깨진 게시판·말머리 ID)
 */
public record AdminPostSearch(Long postId, String text, Long boardId, Long prefixId, String state, boolean oldest, boolean none) {

    public AdminPostSearch withoutState() {
        return new AdminPostSearch(postId, text, boardId, prefixId, null, oldest, none);
    }
}
