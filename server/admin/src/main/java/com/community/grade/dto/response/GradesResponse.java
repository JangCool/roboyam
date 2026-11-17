package com.community.grade.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 등급 목록(서열 순서)과 수동 상향 회원 옵션 — admin-web 의 {@code GradesResponse} 모양이다.
 *
 * @param xpCurveBase     레벨 곡선 기준값(V196, 1~1000, 기본 10)
 * @param xpCurveExponent 레벨 곡선 지수(V245 — JSON 숫자 1.0 · 1.5 · 2.0 · 2.5 · 3.0, 기본 2.0)
 * @param maxGrades       둘 수 있는 최대 등급 수(11)
 * @param pace            등급별 미리보기 표가 쓰는 하루 적립 기준(포인트 정책에서 셈 — grade-level-contract 6절)
 */
public record GradesResponse(List<GradeView> grades, String manualPolicy, int xpCurveBase, BigDecimal xpCurveExponent, int maxGrades, Pace pace) {

    /**
     * 하루 적립 기준 — 경험치는 활동으로 적립된 포인트라 「며칠 걸리는가」를 이 값으로 가늠한다.
     *
     * @param typicalDaily  보통 회원의 하루 적립: 출석 + 글 1개 + 댓글 3개를 일반 등급 게시판에서(글·댓글 하루 횟수 상한이 더 작으면 그만큼만, 하루 총 한도가 있으면 그보다 크지 않게)
     * @param dailyTotalCap 활동 적립 하루 총 한도(0 = 한도 없음)
     */
    public record Pace(int typicalDaily, int dailyTotalCap) {
    }
}
