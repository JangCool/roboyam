package com.community.point.dto.response;

/**
 * 화면이 안내하는 지급 규칙(관리자 정책 — 저장하면 최대 15초 안에 바뀐다). community {@code rules} 모양 + 커머스 1-3 적립 조건.
 *
 * @param attendance             출석 포인트
 * @param post                   글 포인트
 * @param comment                댓글 포인트
 * @param popularReward          인기글 보상 규칙(기능은 아직 없다)
 * @param nicknameTicketPrice    닉네임 변경권 한 장 값 — 포인트 상점 변경권 품목 가격(커머스 1-2 결정 2), 품목이 없으면 null
 * @param likeBonus              추천 받음 보너스 규칙 — {@code points}·{@code dailyCap} 이 0 이면 꺼짐
 * @param postMinLength          글 본문 최소 글자 수(서식 태그·공백 제외, 0 = 없음) — 게시판에 따라 적용하지 않을 수 있다
 * @param commentMinLength       댓글 최소 글자 수(0 = 없음)
 * @param commentOwnPostExcluded 내 글에 단 댓글(답글 포함)은 적립 없음
 * @param commentOncePerPost     한 글에는 댓글 적립 한 번
 * @param writeIntervalSeconds   포인트를 받은 글·댓글 뒤 이 시간(초) 안에 쓴 것은 적립 없음(0 = 없음)
 * @param dailyTotalCap          활동 적립 하루 총 한도(0 = 없음) — {@code today.total.cap} 과 같은 값
 * @param attendanceStreak       연속 출석 보너스
 * @param signup                 본인인증 보너스(가입 포인트)
 */
public record PointRulesView(int attendance, int post, int comment, PopularRewardRuleView popularReward, Integer nicknameTicketPrice, LikeBonusRuleView likeBonus,
                             int postMinLength, int commentMinLength, boolean commentOwnPostExcluded, boolean commentOncePerPost, int writeIntervalSeconds,
                             int dailyTotalCap, AttendanceStreakRuleView attendanceStreak, SignupPointRuleView signup) {
}
