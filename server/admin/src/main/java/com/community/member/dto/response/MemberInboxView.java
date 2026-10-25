package com.community.member.dto.response;

/** 회원 알림함 건수 — 전체·안 읽음(V50 {@code member_notifications}) */
public record MemberInboxView(Long totalCount, Long unreadCount) {
}
