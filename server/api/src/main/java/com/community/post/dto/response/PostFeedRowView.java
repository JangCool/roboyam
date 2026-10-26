package com.community.post.dto.response;

import java.time.Instant;

/**
 * 게시판이 섞인 목록의 한 줄(user-web {@code PostFeedRow}) — 글 목록 한 줄({@link PostRowView})의 칸 + 그 글의 게시판.
 * 최신글·인기글·통합 검색·회원 작성글이 쓴다.
 *
 * @param blocked 보는 사람 기준 차단({@code USER}·{@code WORD}, 내 글·숨긴 글·비로그인 null) — 게시판 글 목록과 같은 판정({@code ViewerBlocks})
 * @param hasPoll 글에 투표가 붙었는가(숨긴 글은 false)
 * @param group   소모임 글이면 {@code { id, name, visibility, dissolved }}(소모임 글 탭 줄의 {@code group} 과 같은 모양 — 이 목록에 나오는 소모임 글은 늘 {@code PUBLIC}),
 *                일반 글이면 null(소모임 6단계 — 화면이 「소모임」 배지를 붙이고 {@code board} 대신 소모임으로 잇는다. 해산한 소모임이면 링크 없이)
 */
public record PostFeedRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                              Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked,
                              boolean hasPoll, PostBoardRefView board, PostGroupView group) {

    public static PostFeedRowView of(PostRowView row, PostBoardRefView board, PostGroupView group) {
        return new PostFeedRowView(row.id(), row.prefix(), row.title(), row.commentCount(), row.hasImage(), row.thumbnailUrl(), row.author(), row.createdAt(),
                row.viewCount(), row.likeCount(), row.pinned(), row.adult(), row.hiddenReason(), row.blocked(), row.hasPoll(), board, group);
    }
}
