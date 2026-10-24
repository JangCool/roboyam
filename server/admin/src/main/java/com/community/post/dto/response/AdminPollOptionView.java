package com.community.post.dto.response;

/** 관리자 글 상세 투표의 선택지 한 줄 — 득표 수(누가 골랐는지는 없다) */
public record AdminPollOptionView(String id, String label, int votes) {
}
