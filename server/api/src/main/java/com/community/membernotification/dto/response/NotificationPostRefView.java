package com.community.membernotification.dto.response;

/**
 * 알림이 가리키는 글 — 읽을 때의 현재 값. 지웠거나 숨겼거나 게시판이 꺼졌으면 {@code available=false} 이고 {@code title} 은 null(화면이 「볼 수 없는 글」을 보인다).
 */
public record NotificationPostRefView(String id, String title, boolean available) {
}
