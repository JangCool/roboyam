package com.community.member.dto.response;

/**
 * 활동 요약 — admin-web {@code MemberActivity}. 글·댓글은 회원 작성분 중 <b>공개·삭제 안 됨·숨김 아님</b>만 센다(임시저장·삭제·숨김 제외, 비회원 글 제외).
 * 추천·인기글 기능은 아직 없어 0, 접속 일수는 로그인 성공 기록이 마지막 로그인 시각 하나뿐이라 0 이다.
 *
 * @param likesReceived     받은 추천 수 — 추천 기능이 없어 0
 * @param popularCount      인기글 수 — 인기글 기능이 없어 0
 * @param recentPostCount   최근 7일 글 수(지금 - 7일 이후에 쓴 글)
 * @param recentCommentCount 최근 7일 댓글 수
 * @param recentLoginDays   최근 7일 중 접속한 날 수(0~7) — 날짜별 로그인 성공 기록이 없어 0
 */
public record MemberActivity(long postCount, long commentCount, long likesReceived, long popularCount, long recentPostCount, long recentCommentCount,
                             int recentLoginDays) {
}
