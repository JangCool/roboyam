package com.community.notification.dto.response;

/** 알림을 누르면 이동할 경로(관리자 앱 안의 경로). 화면이 그 경로의 권한이 없으면 이동하지 않고 읽음만 처리한다 */
public record NotificationLink(String path) {
}
