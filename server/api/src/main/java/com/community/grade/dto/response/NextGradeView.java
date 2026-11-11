package com.community.grade.dto.response;

import java.util.List;

/**
 * 다음 등급과 그 조건(user-web {@code NextGrade}) — 기준이 0 인 조건(레벨은 1)은 싣지 않는다. 차례는 LEVEL · DAYS · ACTIVITY.
 *
 * @param sprite 도트 캐릭터(V196)
 */
public record NextGradeView(String code, String label, List<GradeRequirementView> requirements, String sprite) {
}
