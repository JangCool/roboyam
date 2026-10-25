package com.community.grade.dto.response;

/** 등급 하나의 승급 조건(user-web {@code GradeRule}) — 첫 등급은 0·0(가입 즉시) */
public record GradeRuleView(String code, String label, int minDays, int minActivity) {
}
