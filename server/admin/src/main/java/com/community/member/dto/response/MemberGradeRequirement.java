package com.community.member.dto.response;

/**
 * 다음 등급 조건 한 줄 — admin-web {@code MemberGradeRequirement}. {@code current} 는 상한 없는 실제 값이다.
 *
 * @param kind {@code DAYS}(가입 후 일수) · {@code ACTIVITY}(글+댓글 수)
 */
public record MemberGradeRequirement(String kind, long current, long required) {

    public static final String DAYS = "DAYS";
    public static final String ACTIVITY = "ACTIVITY";
}
