package com.community.profile.dto.response;

/**
 * 공개 프로필의 활동 수치 — 읽을 때 바로 센다.
 *
 * @param posts         공개 목록에 보이는 글 수(지움·숨김·꺼진 게시판 제외)
 * @param comments      지우지 않고 숨기지 않은 댓글 중 볼 수 있는 글에 달린 것
 * @param likesReceived 그 글·댓글이 받은 추천 합
 */
public record ProfileStatsView(long posts, long comments, long likesReceived) {
}
