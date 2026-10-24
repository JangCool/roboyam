package com.community.post.dto.response;

import java.time.Instant;

public record PostFeedRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                              Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, PostBoardRefView board) {
}
