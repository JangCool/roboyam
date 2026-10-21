package com.community.notification.entity;

import java.util.Locale;

/** 알림의 심각도 4단계 — 서랍 아이콘·토스트 색을 고른다. 화면과는 소문자 {@link #kind()} 로 주고받는다 */
public enum NotificationSeverity {

    INFO,
    SUCCESS,
    WARNING,
    /** 로그인 잠금 반복·결제 이상처럼 급한 것 */
    CRITICAL;

    /** 화면과 주고받는 값({@code info} · {@code success} · {@code warning} · {@code critical}) */
    public String kind() {
        return name().toLowerCase(Locale.ROOT);
    }
}
