package com.community.grade.dto.response;

/**
 * 다음 등급의 조건 하나(user-web {@code GradeRequirement}).
 *
 * @param kind    DAYS(가입 후 일수) · ACTIVITY(글+댓글 수)
 * @param current 지금 값(상한 없음)
 * @param met     {@code current >= required}
 */
public record GradeRequirementView(String kind, long current, int required, boolean met) {
}
