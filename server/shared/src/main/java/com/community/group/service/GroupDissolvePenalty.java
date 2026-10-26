package com.community.group.service;

import com.community.group.entity.GroupEntity;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;

/**
 * 해산 패널티 판정(docs/features/group-contract.md 「해산 때 글·패널티」, V96) — 모임장이 스스로 해산하면 그 모임장은 설정 「개설 규칙」의 {@code dissolvePenaltyDays} 동안
 * 새 소모임을 개설 신청할 수 없다(0 = 끔). <b>면제</b>: 개설({@code opened_at})부터 해산까지 1년(달력, KST — 2025-03-01 10:00 KST 에 열렸으면 2026-03-01 10:00 KST 부터)
 * 이상 유지된 소모임. 관리자 비활성화·운영자 처리로 없어진 소모임은 패널티가 아니다(해산 서비스만 부른다).
 */
public final class GroupDissolvePenalty {

    private GroupDissolvePenalty() {
    }

    /** 면제가 시작되는 시각 = 개설 시각 + 1년(KST 달력). 개설 시각이 없으면(열린 적 없음) null */
    public static Instant exemptAt(GroupEntity group) {
        Instant opened = group.getOpenedAt();
        return opened == null ? null : opened.atZone(ServiceTime.ZONE).plusYears(1).toInstant();
    }

    /** 지금 해산하면 면제인가 */
    public static boolean exempt(GroupEntity group, Instant now) {
        Instant at = exemptAt(group);
        return at != null && !now.isBefore(at);
    }

    /** 지금 해산하면 걸리는 패널티의 끝 시각 — 꺼져 있거나(0일) 면제면 null */
    public static Instant penaltyUntil(GroupEntity group, int penaltyDays, Instant now) {
        if (penaltyDays <= 0 || exempt(group, now)) {
            return null;
        }
        return now.plus(Duration.ofDays(penaltyDays));
    }
}
