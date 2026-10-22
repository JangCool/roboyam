package com.community.member.dto.response;

import java.util.List;

/** 다음 등급과 조건 — admin-web {@code MemberGradeNext}. 마지막 등급이면 상위의 {@code next} 가 null 이다 */
public record MemberGradeNext(String gradeKey, List<MemberGradeRequirement> requirements) {
}
