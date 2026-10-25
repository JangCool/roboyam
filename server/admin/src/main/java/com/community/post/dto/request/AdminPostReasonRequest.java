package com.community.post.dto.request;

/** 게시글 관리 조치(숨김 해제·삭제·복구·공지 지정·고정·고정 해제) — {@code reason} 1~200자 필수(서비스가 검사 → 400 {@code fields.reason}) */
public record AdminPostReasonRequest(String reason) {
}
