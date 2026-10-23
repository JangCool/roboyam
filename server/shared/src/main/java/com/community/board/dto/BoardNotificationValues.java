package com.community.board.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기본 설정 하위 화면 「알림」(회원이 받는 게시판 알림의 공통 값) — 관리자 API JSON 모양 그대로(admin-web {@code BoardNotificationSettings}).
 *
 * <p>이벤트별 칸({@code memberDefaults} — 표의 옛 칸 {@code member_defaults})은 회원 이벤트 설정({@code /api/admin/member-events}, V58)으로 옮겼다 — 응답에서 뺐고
 * 요청에 와도 무시한다(알 수 없는 칸은 무시). 남은 것은 묶는 시간 · 이메일 조용한 시간 · 보관 일수다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoardNotificationValues(Integer groupWindowMinutes, Boolean emailQuietHoursEnabled, String emailQuietStart, String emailQuietEnd,
                                      Integer memberNotificationRetentionDays) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("groupWindowMinutes", groupWindowMinutes);
        map.put("emailQuietHoursEnabled", emailQuietHoursEnabled);
        map.put("emailQuietStart", emailQuietStart);
        map.put("emailQuietEnd", emailQuietEnd);
        map.put("memberNotificationRetentionDays", memberNotificationRetentionDays);
        return map;
    }
}
