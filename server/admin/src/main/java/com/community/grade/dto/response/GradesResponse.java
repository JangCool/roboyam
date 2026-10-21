package com.community.grade.dto.response;

import java.util.List;

/** 등급 목록(서열 순서)과 수동 상향 회원 옵션 — admin-web 의 {@code GradesResponse} 모양이다 */
public record GradesResponse(List<GradeView> grades, String manualPolicy) {
}
