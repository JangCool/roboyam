package com.community.notification.entity;

/** 사용자가 만드는 알림 도구의 종류 — Slack · Mattermost 뿐이다. 서랍·이메일·브라우저는 기본 제공(행 없음), 카카오·SMS 는 업체 계약 전이라 만들 수 없다 */
public enum NotificationChannelType {

    SLACK,
    MATTERMOST
}
