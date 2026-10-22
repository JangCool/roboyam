package com.community.post.dto.response;

import java.time.Instant;

/**
 * 글 목록 한 줄(user-web {@code PostRow}). 신고로 숨긴 글은 자리만 — 제목·말머리·작성자 이름이 비고 {@code hiddenReason = REPORTED}.
 *
 * @param thumbnailUrl 썸네일 — 이미지 올리기가 아직 없어 늘 null
 */
public record PostRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                          Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason) {
}
