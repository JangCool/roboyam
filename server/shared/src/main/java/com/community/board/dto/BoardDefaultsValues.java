package com.community.board.dto;

import com.community.board.service.BoardFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 게시판 기본 설정(전역 한 행 {@code board_defaults}) — 관리자 API 의 {@code defaults} JSON 모양 그대로다(admin-web {@code BoardDefaults}).
 * {@code listChipPosition}(V169) = 글 목록 말머리·상태 칩 위치의 사이트 기본 ABOVE · BEFORE · BELOW — 게시판 {@code chipPosition} 이 null 이면 이 값, 섞인 목록도 이 값.
 * {@code listLikeHighlight}·{@code listViewHighlight}·{@code listHighlightColor}(V170) = 글 목록 윗줄 추천·조회 숫자를 강조할 기준(0 = 안 함)과 색 {@code #rrggbb} — 사이트 기본만.
 * 요청으로 받을 때 빠진 칸을 알아보려고 감싼 타입(Integer·Boolean)을 쓴다 — 저장된 값은 모두 채워져 있다. 칸 이름·범위의 원천은 {@link com.community.board.service.BoardFields}.
 */
public record BoardDefaultsValues(String dateDisplay, String writeMin, String commentMin, String postEditor, Boolean imageUploadAllowed, String commentEditor,
                                  Integer postGapSeconds,
                                  Integer postDailyLimit, Integer commentGapSeconds, Integer commentDailyLimit, Integer listSize, Integer pinnedMax,
                                  Boolean indexable, Boolean searchable, Boolean inHome, Boolean attachmentPurgeEnabled, Integer attachmentRetentionDays,
                                  String pointMode, Boolean pointMinLengthApplied, Integer commentProtectMin, Boolean commentProtectEdit, String commentProtectDelete,
                                  Integer commentProtectDelayMinutes, Integer commentGuestDelayMinutes, Boolean staffHiddenNotice,
                                  Boolean commentDislikeEnabled, String commentLikeLabel, String commentDislikeLabel, List<String> pinRequestReviewers,
                                  Boolean debateEnabled, Boolean pollEnabled, Integer bestCommentMinLikes, Integer managerMax,
                                  Integer staffMax, Integer staffPostDailyLimit, Integer staffCommentDailyLimit, Integer staffSanctionDailyLimit,
                                  String listChipPosition, Integer listLikeHighlight, Integer listViewHighlight, String listHighlightColor) {

    /** 추천·비추천 이름은 앞뒤 공백을 자르고 비면 null(= 화면 기본 이름) */
    public BoardDefaultsValues {
        commentLikeLabel = label(commentLikeLabel);
        commentDislikeLabel = label(commentDislikeLabel);
        pinRequestReviewers = BoardFields.canonicalReviewers(pinRequestReviewers);
    }

    /**
     * 나중에 생긴 목록 표시 칸(V169 칩 위치 · V170 숫자 강조)을 채운 사본 — 저장 요청에 칸이 없으면(null) 지금 값({@code current})을 그대로 둔다
     * (이 칸들을 모르는 예전 화면·호출과 맞추려고). 칩 위치는 대문자, 강조 색은 소문자로 맞춘다(앞뒤 공백 뗌 — 모양·범위는 검사에서 400).
     */
    public BoardDefaultsValues filledFrom(BoardDefaultsValues current) {
        String chip = listChipPosition == null ? current.listChipPosition() : listChipPosition.strip().toUpperCase(Locale.ROOT);
        Integer like = listLikeHighlight == null ? current.listLikeHighlight() : listLikeHighlight;
        Integer view = listViewHighlight == null ? current.listViewHighlight() : listViewHighlight;
        String color = listHighlightColor == null ? current.listHighlightColor() : listHighlightColor.strip().toLowerCase(Locale.ROOT);
        return new BoardDefaultsValues(dateDisplay, writeMin, commentMin, postEditor, imageUploadAllowed, commentEditor, postGapSeconds, postDailyLimit,
                commentGapSeconds, commentDailyLimit, listSize, pinnedMax, indexable, searchable, inHome, attachmentPurgeEnabled, attachmentRetentionDays,
                pointMode, pointMinLengthApplied, commentProtectMin, commentProtectEdit, commentProtectDelete, commentProtectDelayMinutes,
                commentGuestDelayMinutes, staffHiddenNotice, commentDislikeEnabled, commentLikeLabel, commentDislikeLabel, pinRequestReviewers, debateEnabled,
                pollEnabled, bestCommentMinLikes, managerMax, staffMax, staffPostDailyLimit, staffCommentDailyLimit, staffSanctionDailyLimit, chip, like, view,
                color);
    }

    /** 단추 이름 정리 — 앞뒤 공백을 자르고 비면 null */
    public static String label(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 칸 이름 → 값(칸 순서대로) */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dateDisplay", dateDisplay);
        map.put("writeMin", writeMin);
        map.put("commentMin", commentMin);
        map.put("postEditor", postEditor);
        map.put("imageUploadAllowed", imageUploadAllowed);
        map.put("commentEditor", commentEditor);
        map.put("postGapSeconds", postGapSeconds);
        map.put("postDailyLimit", postDailyLimit);
        map.put("commentGapSeconds", commentGapSeconds);
        map.put("commentDailyLimit", commentDailyLimit);
        map.put("listSize", listSize);
        map.put("pinnedMax", pinnedMax);
        map.put("indexable", indexable);
        map.put("searchable", searchable);
        map.put("inHome", inHome);
        map.put("attachmentPurgeEnabled", attachmentPurgeEnabled);
        map.put("attachmentRetentionDays", attachmentRetentionDays);
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
        map.put("bestCommentMinLikes", bestCommentMinLikes);
        map.put("managerMax", managerMax);
        map.put("staffMax", staffMax);
        map.put("staffPostDailyLimit", staffPostDailyLimit);
        map.put("staffCommentDailyLimit", staffCommentDailyLimit);
        map.put("staffSanctionDailyLimit", staffSanctionDailyLimit);
        map.put("listChipPosition", listChipPosition);
        map.put("listLikeHighlight", listLikeHighlight);
        map.put("listViewHighlight", listViewHighlight);
        map.put("listHighlightColor", listHighlightColor);
        return map;
    }
}
