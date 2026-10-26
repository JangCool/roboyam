package com.community.group.entity;

/** 공개 요청의 상태(V94 {@code group_post_publish_requests.status}) — 처리 대기 · 승인 · 거절 · 닫힘({@code cancel_reason}) */
public enum GroupPublishRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED
}
