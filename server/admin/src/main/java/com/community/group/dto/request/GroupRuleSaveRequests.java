package com.community.group.dto.request;

/**
 * 소모임 설정 네 구획의 {@code PUT} 본문 — 구획의 칸 모두. 칸은 JSON 값 그대로({@code Object}) 받아 서비스가 형(정수·불리언·글자)과 범위를 본다
 * (문자열 {@code "3"}·소수·빠진 칸은 {@code 400 fields.<칸>}). 각 구획은 아래 중첩 레코드다(한 구획 = 한 본문).
 */
public final class GroupRuleSaveRequests {

    private GroupRuleSaveRequests() {
    }

    /** {@code PUT …/creation-rules} */
    public record Creation(Object creationMode, Object minGrade, Object maxPerOwner, Object dissolvePenaltyDays) {
    }

    /** {@code PUT …/manager-rules} */
    public record Manager(Object managersEnabled, Object limitDefault, Object limitMax) {
    }

    /** {@code PUT …/visibility-rules} */
    public record Visibility(Object visibilityEnabled, Object cooldownDays, Object retryHours, Object rejectLimit, Object dailyLimit, Object textMax) {
    }

    /** {@code PUT …/policy-rules} */
    public record Policy(Object reapplyDays, Object capacityMin, Object capacityMax, Object trendingSize, Object trendingActiveDays, Object eventMaxUpcoming,
                         Object eventMaxDaysAhead, Object eventMaxLengthDays) {
    }
}
