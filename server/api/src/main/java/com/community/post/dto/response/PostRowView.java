package com.community.post.dto.response;

import java.time.Instant;

/**
 * 글 목록 한 줄(user-web {@code PostRow}). 신고·운영자가 숨긴 글은 자리만 — 제목·말머리·작성자 이름이 비고 {@code hiddenReason = REPORTED}
 * (운영자가 숨긴 글은 게시판 옵션 {@code staffHiddenNotice} 가 켜졌을 때만 {@code STAFF} — docs/features/post-admin-contract.md).
 *
 * @param thumbnailUrl 썸네일 — 이미지 올리기가 아직 없어 늘 null
 * @param blocked      보는 사람이 작성 회원을 차단했으면 {@code USER}, 제목에 차단 단어가 들었으면 {@code WORD}, 아니면 null(내 글·비로그인 늘 null). 지금은 게시판 글 목록만 채운다
 */
public record PostRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                          Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked) {

    /** 차단 칸 없이(null) — 차단을 싣지 않는 목록(최신·인기·검색 …) */
    public PostRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                       Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason) {
        this(id, prefix, title, commentCount, hasImage, thumbnailUrl, author, createdAt, viewCount, likeCount, pinned, adult, hiddenReason, null);
    }
}
