package com.community.point.dto.response;

/**
 * 오늘(한국 시간) 현황.
 *
 * @param date      서버가 정한 오늘 {@code YYYY-MM-DD}(한국 시간) — 화면은 브라우저 시계로 오늘을 계산하지 않는다
 * @param attended  오늘 출석했는가(커머스 1-3 — 출석 기능)
 * @param post      글 포인트 — 오늘 받은 횟수(지운 글도 센다)·하루 상한
 * @param comment   댓글 포인트
 * @param likeBonus 추천 보너스(글·댓글 합산)
 * @param total     오늘 활동 적립 합과 하루 총 한도(커머스 1-3)
 */
public record PointTodayView(String date, boolean attended, PointCountView post, PointCountView comment, PointCountView likeBonus, PointTotalView total) {
}
