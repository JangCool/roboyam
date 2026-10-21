package com.community.grade.dto.response;

/** 미리보기의 등급 한 줄 — 이 기준으로 저장하면 이 등급이 될(승급할) 회원 수. 새 등급은 {@code gradeKey} 가 null 이다 */
public record GradePreviewItem(String gradeKey, String label, long count) {
}
