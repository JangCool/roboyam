package com.community.board.dto;

import com.community.board.service.BoardFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게시판별 정책 칸 — 칸마다 {@code null} = 기본 설정을 따른다(유효값 = 게시판 값 ?? 기본값). 관리자 API 의 {@code policy} JSON 모양 그대로다
 * (admin-web {@code BoardPolicy}). 칸 이름·범위의 원천은 {@link com.community.board.service.BoardFields}.
 * {@code authorThumbnailEnabled}(V206) = 「글쓴이가 대표 이미지 고르기」 — 나중에 생긴 칸이라 끝에 둔다(JSON·{@link #toMap} 차례는 {@code pollEnabled} 다음).
 * {@code tagsEnabled}(V215) = 「태그 쓰기」(post-tags-contract) — 같은 이유로 끝({@link #toMap} 차례는 {@code authorThumbnailEnabled} 다음).
 * {@code detailFeedTabsPc}·{@code detailFeedTabsMobile}(V226) = 글 상세 아래 [인기글]·[최신글] 탭 PC·모바일(post-detail-board-list-contract 「2차」),
 * {@code prefixRequired}·{@code tagRequired}(V226) = 말머리 필수·태그 필수(ai-news-reporter-contract 「뉴스 게시판 글 규칙」) — 같은 이유로 끝({@link #toMap} 차례는 {@code tagsEnabled} 다음).
 * {@code sourcesEnabled}(V232) = 「출처 칸 쓰기」(글에 출처 목록 — ai-news-reporter-contract 「본문 꼬리」) — 같은 이유로 끝({@link #toMap} 차례는 {@code tagRequired} 다음).
 */
public record BoardPolicyValues(String writeMin, String commentMin, String postEditor, Boolean imageUploadAllowed, String commentEditor, Integer listSize, Integer pinnedMax, Boolean indexable,
                                Boolean searchable, Boolean inHome, String pointMode, Boolean pointMinLengthApplied, Integer commentProtectMin, Boolean commentProtectEdit,
                                String commentProtectDelete, Integer commentProtectDelayMinutes, Integer commentGuestDelayMinutes, Boolean staffHiddenNotice,
                                Boolean commentDislikeEnabled, String commentLikeLabel, String commentDislikeLabel, List<String> pinRequestReviewers,
                                Boolean debateEnabled, Boolean pollEnabled, Integer managerMax,
                                Integer staffPostDailyLimit, Integer staffCommentDailyLimit, Integer staffSanctionDailyLimit, Boolean authorThumbnailEnabled,
                                Boolean tagsEnabled, Boolean detailFeedTabsPc, Boolean detailFeedTabsMobile, Boolean prefixRequired, Boolean tagRequired,
                                Boolean sourcesEnabled) {

    /** 추천·비추천 이름은 앞뒤 공백을 자르고 비면 null(= 기본값 따름) */
    public BoardPolicyValues {
        commentLikeLabel = BoardDefaultsValues.label(commentLikeLabel);
        commentDislikeLabel = BoardDefaultsValues.label(commentDislikeLabel);
        pinRequestReviewers = BoardFields.canonicalReviewers(pinRequestReviewers);
    }

    /** 모두 기본값을 따르는 정책 */
    public static BoardPolicyValues empty() {
        return new BoardPolicyValues(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** 칸 이름 → 값(칸 순서대로, null 포함) */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("writeMin", writeMin);
        map.put("commentMin", commentMin);
        map.put("postEditor", postEditor);
        map.put("imageUploadAllowed", imageUploadAllowed);
        map.put("commentEditor", commentEditor);
        map.put("listSize", listSize);
        map.put("pinnedMax", pinnedMax);
        map.put("indexable", indexable);
        map.put("searchable", searchable);
        map.put("inHome", inHome);
        map.put("pointMode", pointMode);
        map.put("pointMinLengthApplied", pointMinLengthApplied);
        map.put("commentProtectMin", commentProtectMin);
        map.put("commentProtectEdit", commentProtectEdit);
        map.put("commentProtectDelete", commentProtectDelete);
        map.put("commentProtectDelayMinutes", commentProtectDelayMinutes);
        map.put("commentGuestDelayMinutes", commentGuestDelayMinutes);
        map.put("staffHiddenNotice", staffHiddenNotice);
        map.put("commentDislikeEnabled", commentDislikeEnabled);
        map.put("commentLikeLabel", commentLikeLabel);
        map.put("commentDislikeLabel", commentDislikeLabel);
        map.put("pinRequestReviewers", pinRequestReviewers);
        map.put("debateEnabled", debateEnabled);
        map.put("pollEnabled", pollEnabled);
        map.put("authorThumbnailEnabled", authorThumbnailEnabled);
        map.put("tagsEnabled", tagsEnabled);
        map.put("detailFeedTabsPc", detailFeedTabsPc);
        map.put("detailFeedTabsMobile", detailFeedTabsMobile);
        map.put("prefixRequired", prefixRequired);
        map.put("tagRequired", tagRequired);
        map.put("sourcesEnabled", sourcesEnabled);
        map.put("managerMax", managerMax);
        map.put("staffPostDailyLimit", staffPostDailyLimit);
        map.put("staffCommentDailyLimit", staffCommentDailyLimit);
        map.put("staffSanctionDailyLimit", staffSanctionDailyLimit);
        return map;
    }

    /** 따로 정한(null 이 아닌) 칸 수 */
    public int overriddenCount() {
        return (int) toMap().values().stream().filter(java.util.Objects::nonNull).count();
    }
}
