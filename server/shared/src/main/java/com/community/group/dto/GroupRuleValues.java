package com.community.group.dto;

import com.community.group.entity.GroupCreationMode;

/** 소모임 설정 네 구획의 값(지금 적용되는 값 · 기본값) — {@code GroupSettingsService} 가 만든다 */
public final class GroupRuleValues {

    private GroupRuleValues() {
    }

    /** 개설 규칙 */
    public record Creation(GroupCreationMode creationMode, String minGrade, int maxPerOwner) {
    }

    /** 매니저(3단계가 읽는다) */
    public record Manager(boolean managersEnabled, int limitDefault, int limitMax) {
    }

    /** 공개 범위(5단계가 읽는다) */
    public record Visibility(boolean visibilityEnabled, int cooldownDays, int retryHours, int rejectLimit, int dailyLimit, int textMax) {
    }

    /** 운영 한도 */
    public record Policy(int reapplyDays, int capacityMin, int capacityMax, int trendingSize, int trendingActiveDays, int eventMaxUpcoming, int eventMaxDaysAhead,
                         int eventMaxLengthDays) {
    }
}
