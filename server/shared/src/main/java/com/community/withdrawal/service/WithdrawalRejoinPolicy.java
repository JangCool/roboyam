package com.community.withdrawal.service;

import com.community.restriction.repository.master.MemberRestrictionStore;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재가입 제한 기간(commerce.md 17-1 D3 · 17-2) — <b>max(탈퇴 시각 + 30일(설정값), 진행 중 기능 제한·정지가 끝나는 날)</b>, 무기한 제한·영구 정지는 상한(설정값, 기본 365일).
 * 탈퇴 화면(재가입 가능일 안내)과 완료 처리(해시 {@code expires_at})가 같은 셈을 쓴다.
 */
@Component
@Transactional(readOnly = true)
public class WithdrawalRejoinPolicy {

    /**
     * @param until                   재가입할 수 있는 시각
     * @param extendedByRestriction   제재 때문에 기본 기간보다 늦춰졌는가
     * @param restrictionEndsAt       제재가 끝나는 시각(무기한이면 null — 그때는 상한)
     * @param restrictionReason       제재의 공개 사유(없으면 null)
     */
    public record Rejoin(Instant until, boolean extendedByRestriction, Instant restrictionEndsAt, String restrictionReason) {
    }

    private final MemberRestrictionStore restrictions;
    private final WithdrawalProperties properties;
    private final Messages messages;

    public WithdrawalRejoinPolicy(MemberRestrictionStore restrictions, WithdrawalProperties properties, Messages messages) {
        this.restrictions = restrictions;
        this.properties = properties;
        this.messages = messages;
    }

    /**
     * @param accountStatus 계정 상태({@code users.status}) — {@code SUSPENDED}·{@code BANNED} 는 끝나는 날을 모르므로 상한으로 본다
     */
    public Rejoin of(long userId, String accountStatus, Instant now) {
        Instant base = now.plus(Duration.ofDays(properties.rejoinBlockDays()));
        Instant cap = now.plus(Duration.ofDays(properties.rejoinBlockMaxDays()));
        List<MemberRestrictionStore.Row> active = restrictions.active(userId, now);
        boolean accountSanction = "SUSPENDED".equals(accountStatus) || "BANNED".equals(accountStatus);
        boolean indefinite = accountSanction || active.stream().anyMatch(row -> row.until() == null);
        if (indefinite) {
            String reason = active.stream().filter(row -> row.until() == null).findFirst().map(this::publicReason).orElse(null);
            return new Rejoin(cap, true, null, reason);
        }
        Optional<MemberRestrictionStore.Row> latest = active.stream().max(Comparator.comparing(MemberRestrictionStore.Row::until));
        if (latest.isEmpty() || !latest.get().until().isAfter(base)) {
            return new Rejoin(base, false, latest.map(MemberRestrictionStore.Row::until).orElse(null), latest.map(this::publicReason).orElse(null));
        }
        Instant until = latest.get().until().isAfter(cap) ? cap : latest.get().until();
        return new Rejoin(until, true, latest.get().until(), publicReason(latest.get()));
    }

    private String publicReason(MemberRestrictionStore.Row row) {
        if (row.reason() != null && !row.reason().isBlank()) {
            return row.reason();
        }
        return row.autoRule() == null ? null : messages.get(new MessageRef("restriction.autoRule." + row.autoRule() + ".reason"));
    }
}
