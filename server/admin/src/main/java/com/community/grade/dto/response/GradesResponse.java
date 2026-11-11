package com.community.grade.dto.response;

import java.util.List;

/**
 * 등급 목록(서열 순서)과 수동 상향 회원 옵션 — admin-web 의 {@code GradesResponse} 모양이다.
 *
 * @param xpCurveBase 레벨 곡선 값(V196, 1~1000, 기본 10)
 * @param maxGrades   둘 수 있는 최대 등급 수(11)
 */
public record GradesResponse(List<GradeView> grades, String manualPolicy, int xpCurveBase, int maxGrades) {
}
