package com.community.menu.dto.response;

/** 보이는 대상의 등급 — admin-web {@code MenuGradeOption}. {@code level} 1 이 기본 등급 */
public record MenuGradeOptionView(String gradeKey, String label, int level) {
}
