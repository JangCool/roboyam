package com.community.post.dto.response;

import java.time.Instant;

/**
 * 관리자 화면(인기글 관리·하이라이트)의 글 한 줄.
 *
 * @param boardName   게시판 이름(관리자가 입력한 이름 그대로 — 영어 이름은 {@code boardNameEn}, 없으면 null). 게시판이 없으면 null
 * @param prefixName  말머리 이름(없으면 null)
 * @param authorKind  MEMBER · GUEST
 * @param authorName  회원은 닉네임, 비회원은 입력한 이름
 * @param authorId    회원 ID(비회원 null) — 회원 상세로 이동할 때
 * @param state       PUBLIC · HIDDEN · DELETED · ADULT · BOARD_OFF ({@code AdminPostRow#stateAt})
 */
public record AdminPostRefView(String id, String title, String boardId, String boardName, String boardNameEn, String prefixName, String authorKind,
                               String authorId, String authorName, int likeCount, int commentCount, int viewCount, Instant createdAt, String state) {
}
