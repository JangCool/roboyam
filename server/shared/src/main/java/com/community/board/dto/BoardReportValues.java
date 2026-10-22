package com.community.board.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** 기본 설정 하위 화면 「신고 정책」 — 관리자 API JSON 모양 그대로(admin-web {@code BoardReportSettings}) */
public record BoardReportValues(Boolean reportEnabled, Integer autoHideThreshold, String autoHideTarget, String autoHideMinGrade,
                                Boolean autoHideExemptStaffPosts, Integer rightsTempMeasureDays, Integer dailyReportLimit, Integer reportCooldownSeconds,
                                Integer rejectionLimitCount, Integer rejectionLimitDays, Integer rejectionBlockDays, String notifyOperatorScope,
                                Boolean notifyAuthorOnAction, Boolean notifyReporterOnResult) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reportEnabled", reportEnabled);
        map.put("autoHideThreshold", autoHideThreshold);
        map.put("autoHideTarget", autoHideTarget);
        map.put("autoHideMinGrade", autoHideMinGrade);
        map.put("autoHideExemptStaffPosts", autoHideExemptStaffPosts);
        map.put("rightsTempMeasureDays", rightsTempMeasureDays);
        map.put("dailyReportLimit", dailyReportLimit);
        map.put("reportCooldownSeconds", reportCooldownSeconds);
        map.put("rejectionLimitCount", rejectionLimitCount);
        map.put("rejectionLimitDays", rejectionLimitDays);
        map.put("rejectionBlockDays", rejectionBlockDays);
        map.put("notifyOperatorScope", notifyOperatorScope);
        map.put("notifyAuthorOnAction", notifyAuthorOnAction);
        map.put("notifyReporterOnResult", notifyReporterOnResult);
        return map;
    }
}
