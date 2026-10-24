package com.community.post.dto;

import java.time.Instant;

/**
 * 게시글 관리 목록의 거르기(서비스가 요청 값을 풀어 만든다). 검색은 {@code searchField} 로 고른 한 칸만 본다(본문 검색 없음).
 *
 * @param postId      글 번호 정확히 일치({@code searchField=ID}) — 아니면 null
 * @param title       제목 부분 일치(2자 이상, 대소문자 무시) — 아니면 null
 * @param author      작성자 닉네임(회원) · 비회원 이름 부분 일치(2자 이상, 대소문자 무시) — 아니면 null
 * @param createdFrom 작성 시각 하한(포함) — null 이면 없음
 * @param createdTo   작성 시각 상한(포함) — null 이면 없음
 * @param boardId     게시판 — null 이면 전체
 * @param prefixId    말머리(게시판을 고른 뒤) — null 이면 전체
 * @param state       PUBLIC · HIDDEN · DELETED — null 이면 전체(ALL)
 * @param oldest      오래된순(아니면 최신순)
 * @param none        아무 글도 맞지 않는 거르기(깨진 게시판·말머리 ID, 있을 수 없는 글 번호)
 */
public record AdminPostSearch(Long postId, String title, String author, Instant createdFrom, Instant createdTo, Long boardId, Long prefixId, String state,
                              boolean oldest, boolean none) {
}
