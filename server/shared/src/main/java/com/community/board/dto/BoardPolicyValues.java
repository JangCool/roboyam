package com.community.board.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 게시판별 정책 칸 — 칸마다 {@code null} = 기본 설정을 따른다(유효값 = 게시판 값 ?? 기본값). 관리자 API 의 {@code policy} JSON 모양 그대로다
 * (admin-web {@code BoardPolicy}). 칸 이름·범위의 원천은 {@link com.community.board.service.BoardFields}.
 */
public record BoardPolicyValues(String writeMin, String commentMin, String postEditor, Boolean imageUploadAllowed, String commentEditor, Integer listSize, Integer pinnedMax, Boolean indexable,
                                Boolean searchable, Boolean inHome, String pointMode, Integer commentProtectMin, Boolean commentProtectEdit,
                                String commentProtectDelete, Integer commentProtectDelayMinutes, Integer commentGuestDelayMinutes, Integer managerMax,
                                Integer staffPostDailyLimit, Integer staffCommentDailyLimit, Integer staffSanctionDailyLimit) {

    /** 모두 기본값을 따르는 정책 */
    public static BoardPolicyValues empty() {
        return new BoardPolicyValues(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
        map.put("commentProtectMin", commentProtectMin);
        map.put("commentProtectEdit", commentProtectEdit);
        map.put("commentProtectDelete", commentProtectDelete);
        map.put("commentProtectDelayMinutes", commentProtectDelayMinutes);
        map.put("commentGuestDelayMinutes", commentGuestDelayMinutes);
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
