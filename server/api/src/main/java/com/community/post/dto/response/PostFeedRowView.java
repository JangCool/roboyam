package com.community.post.dto.response;

import java.time.Instant;

/**
 * 게시판이 섞인 목록의 한 줄(user-web {@code PostFeedRow}) — 글 목록 한 줄({@link PostRowView})의 칸 + 그 글의 게시판.
 * 최신글·인기글·통합 검색·회원 작성글이 쓴다.
 */
public record PostFeedRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                              Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, PostBoardRefView board) {

    public static PostFeedRowView of(PostRowView row, PostBoardRefView board) {
        return new PostFeedRowView(row.id(), row.prefix(), row.title(), row.commentCount(), row.hasImage(), row.thumbnailUrl(), row.author(), row.createdAt(),
                row.viewCount(), row.likeCount(), row.pinned(), row.adult(), row.hiddenReason(), board);
    }
}
