package com.community.post.dto.response;

import com.community.hotdeal.dto.response.PostDealView;
import java.time.Instant;

/**
 * 글 목록 한 줄(user-web {@code PostRow}). 신고·운영자가 숨긴 글은 자리만 — 제목·말머리·작성자 이름이 비고 {@code hiddenReason = REPORTED}
 * (운영자가 숨긴 글은 게시판 옵션 {@code staffHiddenNotice} 가 켜졌을 때만 {@code STAFF} — docs/features/post-admin-contract.md).
 *
 * @param thumbnailUrl 썸네일 — 첫 글 이미지, 없으면 첫 YouTube 영상 썸네일, 둘 다 없으면 null({@code PostThumbnails})
 * @param blocked      보는 사람이 작성 회원을 차단했으면 {@code USER}, 제목에 차단 단어가 들었으면 {@code WORD}, 아니면 null(내 글·비로그인 늘 null). 지금은 게시판 글 목록만 채운다
 * @param hasPoll      글에 투표가 붙었는가(숨긴 글은 false) — 제목 옆 투표 칩(docs/features/post-poll-contract.md)
 * @param deal         핫딜 게시판 목록의 줄이면 딜(숨긴 글·그 밖 목록은 null — docs/features/hotdeal-contract.md)
 * @param secret       비밀글인가(V166) — 줄과 제목은 그대로 두고, 읽을 수 없는 사람에게는 썸네일·이미지·투표 표시를 비운다(글을 열면 403 SECRET_POST)
 * @param processStatus 처리 상태(V166) — 게시판이 상태를 쓰지 않으면 null, 쓰면 글 값(없으면 RECEIVED). 숨긴 글은 null
 * @param purchaseSource 구입 경로(V241 — TRIAL·SELF·SPONSORED·BORROWED, 없으면 null) — 목록 칩. 게시판 글 목록만 채운다(숨긴 글 null)
 * @param purchaseProofApproved 구매 인증이 승인된 글인가 — 「구매 인증」 칩
 * @param debate       찬반 토론 요약(찬·반·중 수) — 토론 글만, 그 밖·숨긴 글·읽을 수 없는 비밀글은 null. 게시판 글 목록만 채운다(섞인 목록은 {@link PostFeedRowView#debate}) — feed-debate-counts-contract
 * @param counsel      AI 연애상담소 상태(V274 — 「AI 상담소」 말머리 글만, 그 밖·숨긴 글은 null). 게시판 글 목록만 채운다 — ai-love-counsel-contract
 */
public record PostRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                          Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked, boolean hasPoll,
                          PostDealView deal, boolean secret, String processStatus, PostFeedDebateView debate, String purchaseSource, boolean purchaseProofApproved,
                          PostCounselRowView counsel) {

    /** 상담 칸 없이(프로필 글 목록 등 — 늘 비어 있다) */
    public PostRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                       Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked, boolean hasPoll,
                       PostDealView deal, boolean secret, String processStatus, PostFeedDebateView debate, String purchaseSource, boolean purchaseProofApproved) {
        this(id, prefix, title, commentCount, hasImage, thumbnailUrl, author, createdAt, viewCount, likeCount, pinned, adult, hiddenReason, blocked, hasPoll, deal, secret,
                processStatus, debate, purchaseSource, purchaseProofApproved, null);
    }

    /** 구입 경로 칸 없이(섞인 목록 — 늘 비어 있다) */
    public PostRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                       Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked, boolean hasPoll,
                       PostDealView deal, boolean secret, String processStatus, PostFeedDebateView debate) {
        this(id, prefix, title, commentCount, hasImage, thumbnailUrl, author, createdAt, viewCount, likeCount, pinned, adult, hiddenReason, blocked, hasPoll, deal, secret,
                processStatus, debate, null, false, null);
    }
}
