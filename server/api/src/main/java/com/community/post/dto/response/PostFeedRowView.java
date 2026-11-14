package com.community.post.dto.response;

import com.community.hotdeal.dto.response.PostDealView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 게시판이 섞인 목록의 한 줄(user-web {@code PostFeedRow}) — 글 목록 한 줄({@link PostRowView})의 칸 + 그 글의 게시판.
 * 최신글·인기글·통합 검색·회원 작성글·가젯이 쓴다.
 *
 * @param blocked 보는 사람 기준 차단({@code USER}·{@code WORD}, 내 글·숨긴 글·비로그인 null) — 게시판 글 목록과 같은 판정({@code ViewerBlocks})
 * @param hasPoll 글에 투표가 붙었는가(숨긴 글은 false)
 * @param group   소모임 글이면 {@code { id, name, visibility, dissolved }}(소모임 글 탭 줄의 {@code group} 과 같은 모양 — 이 목록에 나오는 소모임 글은 늘 {@code PUBLIC}),
 *                일반 글이면 null(소모임 6단계 — 화면이 「소모임」 배지를 붙이고 {@code board} 대신 소모임으로 잇는다. 해산한 소모임이면 링크 없이)
 * @param deal    핫딜 게시판 글이면 딜(핫딜 목록 줄과 같은 모양 — hotdeal-contract, 서버 2단계), 그 밖·숨긴 글·회원 작성글 목록은 null
 * @param secret  비밀글인가(V166) — 섞인 목록에는 비밀글이 나오지 않으므로 회원 작성글을 본인이 볼 때만 true 가 될 수 있다
 * @param processStatus 처리 상태(V166 — 게시판이 상태를 쓰지 않으면 null)
 * @param debate  찬반 토론 요약(토론 글만 — {@link PostFeedDebateView}, 그 밖·숨긴 글 null). 줄을 만드는 쪽이 쪽마다 한 번에 센다({@code PostFeedRows#debates})
 * @param excerpt 본문 앞부분 글자(home-news-contract — 글 목록 가젯 {@code lead: THUMB} 의 첫 글만, 가려진 글·19금 글·본문 글자가 없으면 null).
 *                <b>null 이면 응답에서 빠진다</b>(다른 목록·다른 글은 칸 자체가 없다 — 화면은 빠진 칸을 null 로 읽는다)
 */
public record PostFeedRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                              Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked,
                              boolean hasPoll, PostBoardRefView board, PostGroupView group, PostDealView deal, boolean secret, String processStatus,
                              PostFeedDebateView debate, @JsonInclude(JsonInclude.Include.NON_NULL) String excerpt) {

    public PostFeedRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                           Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked,
                           boolean hasPoll, PostBoardRefView board, PostGroupView group, PostDealView deal, boolean secret, String processStatus,
                           PostFeedDebateView debate) {
        this(id, prefix, title, commentCount, hasImage, thumbnailUrl, author, createdAt, viewCount, likeCount, pinned, adult, hiddenReason, blocked, hasPoll, board,
                group, deal, secret, processStatus, debate, null);
    }

    /** 같은 줄에 보는 사람 몫(작성자 메모 · 차단 · 딜의 내 종료 요청)만 바꾼 것 — 공용 캐시 줄에 회원 기준으로 덧입힐 때 */
    public PostFeedRowView forViewer(String memo, String blockedValue, PostDealView dealValue) {
        return new PostFeedRowView(id, prefix, title, commentCount, hasImage, thumbnailUrl, author == null ? null : author.withMemo(memo), createdAt, viewCount, likeCount,
                pinned, adult, hiddenReason, blockedValue, hasPoll, board, group, dealValue, secret, processStatus, debate, excerpt);
    }

    /** 같은 줄에 본문 앞부분 글자만 붙인 것 — 글 목록 가젯 {@code lead: THUMB} 의 첫 글 */
    public PostFeedRowView withExcerpt(String value) {
        return new PostFeedRowView(id, prefix, title, commentCount, hasImage, thumbnailUrl, author, createdAt, viewCount, likeCount, pinned, adult, hiddenReason, blocked,
                hasPoll, board, group, deal, secret, processStatus, debate, value);
    }

    public static PostFeedRowView of(PostRowView row, PostBoardRefView board, PostGroupView group, PostFeedDebateView debate) {
        return new PostFeedRowView(row.id(), row.prefix(), row.title(), row.commentCount(), row.hasImage(), row.thumbnailUrl(), row.author(), row.createdAt(),
                row.viewCount(), row.likeCount(), row.pinned(), row.adult(), row.hiddenReason(), row.blocked(), row.hasPoll(), board, group, row.deal(), row.secret(),
                row.processStatus(), debate);
    }
}
