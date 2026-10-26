package com.community.group.dto;

import com.community.group.entity.GroupCreationMode;

/** 소모임 설정 네 구획의 값(지금 적용되는 값 · 기본값) — {@code GroupSettingsService} 가 만든다 */
public final class GroupRuleValues {

    private GroupRuleValues() {
    }

    /** 개설 규칙 — {@code dissolvePenaltyDays} 해산 패널티 일수(0 = 끔, V96) */
    public record Creation(GroupCreationMode creationMode, String minGrade, int maxPerOwner, int dissolvePenaltyDays) {
    }

    /** 매니저(3단계가 읽는다) */
    public record Manager(boolean managersEnabled, int limitDefault, int limitMax) {

        /**
         * 소모임의 유효 상한 = min(소모임에 정해 둔 상한, 최대 인원) — 새 임명의 409 {@code GROUP_MANAGER_LIMIT_REACHED}·상세 {@code managerInfo.limit}·매니저 목록 {@code limit}.
         * 저장된 소모임 값은 그대로라 최대 인원을 다시 늘리면 원래 값이 살아난다(community api-groups.md 5-1).
         */
        public int effectiveLimit(int groupLimit) {
            return Math.max(1, Math.min(groupLimit, limitMax));
        }
    }

    /** 공개 범위(5단계가 읽는다) */
    public record Visibility(boolean visibilityEnabled, int cooldownDays, int retryHours, int rejectLimit, int dailyLimit, int textMax) {
    }

    /** 운영 한도 */
    public record Policy(int reapplyDays, int capacityMin, int capacityMax, int trendingSize, int trendingActiveDays, int eventMaxUpcoming, int eventMaxDaysAhead,
                         int eventMaxLengthDays) {
    }
}
