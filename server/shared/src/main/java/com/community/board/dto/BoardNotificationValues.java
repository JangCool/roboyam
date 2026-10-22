package com.community.board.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 기본 설정 하위 화면 「알림」(회원이 받는 게시판 알림의 기본값) — 관리자 API JSON 모양 그대로(admin-web {@code BoardNotificationSettings}) */
public record BoardNotificationValues(List<MemberNotifyRow> memberDefaults, Integer groupWindowMinutes, Boolean emailQuietHoursEnabled, String emailQuietStart,
                                      String emailQuietEnd, Integer memberNotificationRetentionDays) {

    /** 회원 알림 기본값 한 줄 */
    public record MemberNotifyRow(String kind, Boolean site, Boolean email, Boolean memberCanTurnOff) {
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("memberDefaults", memberDefaults);
        map.put("groupWindowMinutes", groupWindowMinutes);
        map.put("emailQuietHoursEnabled", emailQuietHoursEnabled);
        map.put("emailQuietStart", emailQuietStart);
        map.put("emailQuietEnd", emailQuietEnd);
        map.put("memberNotificationRetentionDays", memberNotificationRetentionDays);
        return map;
    }
}
