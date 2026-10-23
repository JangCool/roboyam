package com.community.notificationtemplate.catalog;

import com.community.notification.entity.NotificationSeverity;

/**
 * 문구 대상의 심각도 — Slack · Mattermost 템플릿의 색 막대·심각도 그림을 고른다(운영자 이벤트만). 알림 심각도 4단계 중 {@code SUCCESS} 는 안내({@link #INFO})로 본다.
 */
public enum MessageSeverity {

    INFO,
    WARNING,
    CRITICAL;

    public static MessageSeverity of(NotificationSeverity severity) {
        if (severity == null) {
            return INFO;
        }
        return switch (severity) {
            case WARNING -> WARNING;
            case CRITICAL -> CRITICAL;
            case INFO, SUCCESS -> INFO;
        };
    }

    /** Slack · Mattermost 그림 이름 — 지금 {@code ChannelMessageFormatter} 와 같다 */
    public String emoji() {
        return switch (this) {
            case CRITICAL -> ":rotating_light:";
            case WARNING -> ":warning:";
            case INFO -> ":information_source:";
        };
    }
}
