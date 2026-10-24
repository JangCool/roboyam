package com.community.board.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 게시판 기본 설정(전역 한 행 {@code board_defaults}) — 관리자 API 의 {@code defaults} JSON 모양 그대로다(admin-web {@code BoardDefaults}).
 * 요청으로 받을 때 빠진 칸을 알아보려고 감싼 타입(Integer·Boolean)을 쓴다 — 저장된 값은 모두 채워져 있다. 칸 이름·범위의 원천은 {@link com.community.board.service.BoardFields}.
 */
public record BoardDefaultsValues(String dateDisplay, String writeMin, String commentMin, String postEditor, Boolean imageUploadAllowed, String commentEditor,
                                  Integer postGapSeconds,
                                  Integer postDailyLimit, Integer commentGapSeconds, Integer commentDailyLimit, Integer listSize, Integer pinnedMax,
                                  Boolean indexable, Boolean searchable, Boolean inHome, Boolean attachmentPurgeEnabled, Integer attachmentRetentionDays,
                                  String pointMode, Integer commentProtectMin, Boolean commentProtectEdit, String commentProtectDelete,
                                  Integer commentProtectDelayMinutes, Integer commentGuestDelayMinutes, Boolean staffHiddenNotice, Integer bestCommentMinLikes, Integer managerMax,
                                  Integer staffMax, Integer staffPostDailyLimit, Integer staffCommentDailyLimit, Integer staffSanctionDailyLimit) {

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
        map.put("commentProtectMin", commentProtectMin);
        map.put("commentProtectEdit", commentProtectEdit);
        map.put("commentProtectDelete", commentProtectDelete);
        map.put("commentProtectDelayMinutes", commentProtectDelayMinutes);
        map.put("commentGuestDelayMinutes", commentGuestDelayMinutes);
        map.put("staffHiddenNotice", staffHiddenNotice);
        map.put("bestCommentMinLikes", bestCommentMinLikes);
        map.put("managerMax", managerMax);
        map.put("staffMax", staffMax);
        map.put("staffPostDailyLimit", staffPostDailyLimit);
        map.put("staffCommentDailyLimit", staffCommentDailyLimit);
        map.put("staffSanctionDailyLimit", staffSanctionDailyLimit);
        return map;
    }
}
