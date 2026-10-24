package com.community.post.dto.response;

import java.util.List;

public record PostFeedPageView(List<PostFeedRowView> items, boolean hasNext, String nextCursor) {
}
