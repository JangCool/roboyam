package com.community.board.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** 기본 설정 하위 화면 「개인정보 보유기간」 — 관리자 API JSON 모양 그대로(admin-web {@code BoardPrivacySettings}). 적용은 DB 배치 {@code board-privacy-retention} */
public record BoardPrivacyValues(Boolean retentionEnabled, Integer ipRetentionDays, Integer guestRetentionDays, String guestMode,
                                 Integer deletedContentRetentionDays, Integer reportRecordRetentionDays, Integer staffActionRetentionDays) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("retentionEnabled", retentionEnabled);
        map.put("ipRetentionDays", ipRetentionDays);
        map.put("guestRetentionDays", guestRetentionDays);
        map.put("guestMode", guestMode);
        map.put("deletedContentRetentionDays", deletedContentRetentionDays);
        map.put("reportRecordRetentionDays", reportRecordRetentionDays);
        map.put("staffActionRetentionDays", staffActionRetentionDays);
        return map;
    }
}
