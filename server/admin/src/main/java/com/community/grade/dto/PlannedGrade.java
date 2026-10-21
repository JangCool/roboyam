package com.community.grade.dto;

/**
 * 검사를 마친 등급 한 줄 — 서열 순서 목록의 한 칸. 첫 등급의 조건은 0 이다.
 *
 * @param gradeKey 기존 등급의 키. 새로 만들 등급이면 null
 */
public record PlannedGrade(String gradeKey, int level, String label, int minDays, int minActivity, String badgeColor, String badgeIcon) {
}
