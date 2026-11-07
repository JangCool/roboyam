package com.community.tips.dto.response;

import com.community.hotdeal.dto.response.PostDealView;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.dto.response.PostBoardRefView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.dto.response.PostGroupView;
import com.community.post.dto.response.PostPrefixRefView;
import java.time.Instant;

/**
 * 팁 묶음의 글 한 줄(user-web {@code TipsPost}) — 섞인 목록 줄({@link PostFeedRowView})의 칸 그대로 + 본문 요약 {@code excerpt}.
 * 태그({@code tags})는 서버에 없어 싣지 않는다(tips-contract 「미확정」).
 *
 * @param excerpt 본문 평문 앞 약 120자(공백 정리, 넘으면 「…」) — 가려진 글·19금 글·본문이 빈 글은 null. 헤드라인의 하이라이트 줄은 운영자 요약이 있으면 그것
 */
public record TipsPostView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                           Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked,
                           boolean hasPoll, PostBoardRefView board, PostGroupView group, PostDealView deal, String excerpt) {

    public static TipsPostView of(PostFeedRowView r, String excerpt) {
        return new TipsPostView(r.id(), r.prefix(), r.title(), r.commentCount(), r.hasImage(), r.thumbnailUrl(), r.author(), r.createdAt(), r.viewCount(),
                r.likeCount(), r.pinned(), r.adult(), r.hiddenReason(), r.blocked(), r.hasPoll(), r.board(), r.group(), r.deal(), excerpt);
    }

    /**
     * 하이라이트 줄 — 운영자가 적은 노출 제목·요약·올린 대표 이미지가 있으면 그것으로(없는 칸은 글 값).
     */
    public static TipsPostView highlighted(PostFeedRowView r, String excerpt, String title, String summary, String imageUrl) {
        return new TipsPostView(r.id(), r.prefix(), title != null ? title : r.title(), r.commentCount(), r.hasImage() || imageUrl != null,
                imageUrl != null ? imageUrl : r.thumbnailUrl(), r.author(), r.createdAt(), r.viewCount(), r.likeCount(), r.pinned(), r.adult(), r.hiddenReason(),
                r.blocked(), r.hasPoll(), r.board(), r.group(), r.deal(), summary != null ? summary : excerpt);
    }
}
