package com.community.grade.dto;

/**
 * 승급 판정이 쓰는 등급 한 단계의 조건 — 서열 순서 목록의 한 칸.
 *
 * @param gradeKey 등급 키. 아직 만들지 않은 등급(저장 전 미리보기)이면 null
 */
public record GradeThreshold(String gradeKey, int level, int minDays, int minActivity) {
}
