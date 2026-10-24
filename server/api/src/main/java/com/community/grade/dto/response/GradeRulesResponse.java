package com.community.grade.dto.response;

import java.util.List;

/** {@code GET /api/grade-rules}(user-web {@code GradeRules}) — 등급 서열 순(낮은 등급부터) */
public record GradeRulesResponse(List<GradeRuleView> rules) {
}
