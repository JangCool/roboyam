package com.community.grade.dto.response;

import java.util.List;

/** 다음 등급과 그 조건(user-web {@code NextGrade}) — 기준이 0 인 조건은 싣지 않는다 */
public record NextGradeView(String code, String label, List<GradeRequirementView> requirements) {
}
