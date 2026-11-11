package com.community.grade.dto.response;

/**
 * 등급 하나의 승급 조건(user-web {@code GradeRule}) — 첫 등급은 레벨 1·0일·0(가입 즉시).
 *
 * @param minLevel 최소 레벨(V196)
 * @param level    서열(1..N)
 * @param sprite   도트 캐릭터(V196)
 */
public record GradeRuleView(String code, String label, int minDays, int minActivity, int minLevel, int level, String sprite) {
}
