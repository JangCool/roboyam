package com.community.group.service;

import com.community.group.dto.GroupRuleValues;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupVisibility;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 공개 범위 기능(5단계)을 읽는 한 곳 — 켜짐·숫자 다섯(관리자 설정 「공개 범위」 구획, 저장된 값 ?? 설정 파일 — {@link GroupSettingsService#visibility()})과
 * 응답의 {@code visibility} null 규칙·쿨다운(community api-groups.md 11-2·11-4·11-10). 캐시하지 않는다(저장하면 다음 요청부터 모든 서버에 적용).
 */
@Component
public class GroupVisibilityPolicy {

    private final GroupSettingsService settings;

    public GroupVisibilityPolicy(GroupSettingsService settings) {
        this.settings = settings;
    }

    /** 지금 적용되는 공개 범위 구획(켜짐 · 쿨다운 · 재요청 대기 · 거절 한도 · 하루 요청 수 · 글자 수) */
    public GroupRuleValues.Visibility rules() {
        return settings.visibility();
    }

    /** 공개 범위 기능이 켜져 있나 */
    public boolean enabled() {
        return rules().visibilityEnabled();
    }

    /** 공개 사이트 응답의 {@code visibility} — 공개 소모임은 늘 PUBLIC, 비공개 소모임은 기능이 켜져 있을 때만 PRIVATE(꺼져 있으면 null) */
    public static String exposed(GroupEntity group, boolean enabled) {
        if (group.getVisibility() == GroupVisibility.PUBLIC) {
            return GroupVisibility.PUBLIC.name();
        }
        return enabled ? GroupVisibility.PRIVATE.name() : null;
    }

    /**
     * 비공개→공개 쿨다운이 끝나는 시각 — 마지막 변경이 없거나(개설 때 정한 값) 이미 지났으면 null. 변경은 방향과 무관하게 마지막 변경 시각을 쓴다.
     */
    public static Instant canChangeToPublicAt(GroupEntity group, int cooldownDays, Instant now) {
        Instant changedAt = group.getVisibilityChangedAt();
        if (changedAt == null || cooldownDays <= 0) {
            return null;
        }
        Instant end = changedAt.plus(Duration.ofDays(cooldownDays));
        return end.isAfter(now) ? end : null;
    }
}
