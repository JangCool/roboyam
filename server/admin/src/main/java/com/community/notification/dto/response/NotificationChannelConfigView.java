package com.community.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 비밀이 아닌 설정 값 — 비어 있는 칸은 키를 내지 않는다. 비밀(봇 토큰·웹훅 주소)은 응답에 없다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationChannelConfigView(String channel, String mattermostMode, String serverUrl) {

    public static NotificationChannelConfigView empty() {
        return new NotificationChannelConfigView(null, null, null);
    }
}
