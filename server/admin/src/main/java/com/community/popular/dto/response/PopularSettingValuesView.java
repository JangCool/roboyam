package com.community.popular.dto.response;

/**
 * 인기글 설정 값 11칸(설정·기본값 두 곳에 같은 모양).
 *
 * @param likeWeight         추천 가중치
 * @param commentWeight      댓글 가중치
 * @param viewWeight         조회 가중치
 * @param gravity            시간 감쇠 지수(실시간 탭만)
 * @param ageOffsetHours     시간 감쇠 보정(시)
 * @param minLikes           순위에 오르는 최소 추천 수
 * @param minComments        최소 댓글 수
 * @param minViews           최소 조회 수
 * @param excludeStaffBoards 운영진 전용 게시판(유효 글쓰기 자격 STAFF)의 글을 뺀다
 * @param maxPerAuthor       한 사람이 한 기간 순위에 오를 수 있는 글 수(채운 글 포함)
 * @param weekStart          「이번 주」 시작 요일 MONDAY · SUNDAY
 */
public record PopularSettingValuesView(double likeWeight, double commentWeight, double viewWeight, double gravity, double ageOffsetHours, int minLikes,
                                       int minComments, int minViews, boolean excludeStaffBoards, int maxPerAuthor, String weekStart) {
}
