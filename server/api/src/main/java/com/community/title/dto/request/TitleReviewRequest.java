package com.community.title.dto.request;

/** 칭호 신청 승인·거절 본문 — {@code reason} 거절은 필수(1~500자), 승인은 선택(메모) */
public record TitleReviewRequest(String reason) {
}
