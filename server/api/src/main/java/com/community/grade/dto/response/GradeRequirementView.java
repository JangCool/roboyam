package com.community.grade.dto.response;

/**
 * 다음 등급의 조건 하나(user-web {@code GradeRequirement}).
 *
 * @param kind    LEVEL(최소 레벨 — V196) · DAYS(가입 후 일수) · ACTIVITY(글+댓글 수)
 * @param current 지금 값(상한 없음)
 * @param met     {@code current >= required}
 * @param xpLeft  LEVEL 줄만 — 그 최소 레벨이 시작하는 누적 경험치까지 남은 경험치(0 이상, 이미 채웠으면 0 — grade-level-contract 6절). 다른 종류는 null
 */
public record GradeRequirementView(String kind, long current, int required, boolean met, Long xpLeft) {

    /** 경험치 칸이 없는 종류(DAYS · ACTIVITY) */
    public GradeRequirementView(String kind, long current, int required, boolean met) {
        this(kind, current, required, met, null);
    }
}
