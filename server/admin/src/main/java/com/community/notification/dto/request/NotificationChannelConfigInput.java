package com.community.notification.dto.request;

/** 비밀이 아닌 설정 값 — Slack: {@code channel} / Mattermost: {@code mattermostMode}({@code webhook}|{@code bot}), 봇 방식이면 {@code serverUrl}·{@code channel}. 종류가 쓰지 않는 칸은 버린다 */
public record NotificationChannelConfigInput(String channel, String mattermostMode, String serverUrl) {
}
