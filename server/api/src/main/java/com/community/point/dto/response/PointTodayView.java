package com.community.point.dto.response;

/**
 * 오늘(한국 시간) 현황.
 *
 * @param date      서버가 정한 오늘 {@code YYYY-MM-DD}(한국 시간) — 화면은 브라우저 시계로 오늘을 계산하지 않는다
 * @param attended  오늘 출석했는가 — 출석 기능이 아직 없어 늘 false({@link PointSummaryView#attendanceAvailable()} 가 false)
 * @param post      글 포인트 — 오늘 받은 횟수(지운 글도 센다)·하루 상한
 * @param comment   댓글 포인트
 * @param likeBonus 추천 보너스(글·댓글 합산)
 */
public record PointTodayView(String date, boolean attended, PointCountView post, PointCountView comment, PointCountView likeBonus) {
}
