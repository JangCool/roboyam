package com.community.member.dto.response;

/**
 * 활동 요약 — admin-web {@code MemberActivity}. 글·댓글은 회원 작성분 중 <b>공개 게시판에 지금 보이는 것</b>만 센다(임시저장·삭제·예약 삭제 시각 지남·숨김 중 제외,
 * 임시조치가 끝난 것은 다시 센다, 비회원 글 제외 — 정의는 shared {@code MemberActivityRepository}). 인기글 기능은 아직 없어 0, 접속 일수는 로그인 성공 기록이 마지막 로그인 시각
 * 하나뿐이라 0 이다.
 *
 * @param likesReceived     받은 추천 수 — 위 기준으로 보이는 글·댓글의 추천 수({@code like_count}) 합
 * @param popularCount      인기글 수 — 인기글 기능이 없어 0
 * @param recentPostCount   최근 7일 글 수(지금 - 7일 이후에 쓴 글)
 * @param recentCommentCount 최근 7일 댓글 수
 * @param recentLoginDays   최근 7일 중 접속한 날 수(0~7) — 날짜별 로그인 성공 기록이 없어 0
 */
public record MemberActivity(long postCount, long commentCount, long likesReceived, long popularCount, long recentPostCount, long recentCommentCount,
                             int recentLoginDays) {
}
