package com.community.trial.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.trial.entity.TrialSettingEntity;
import com.community.trial.entity.TrialSettingEntity.Values;
import com.community.trial.repository.master.TrialSettingRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험단 설정(trial-group-contract 「관리자 화면」·「구현 결정」 2) — 행 하나. 저장은 칸마다 범위를 보고(400 {@code fields.<칸>}) 바뀐 칸만 감사 기록({@code TRIAL_SETTINGS_UPDATE}, before/after).
 * 빠진 칸은 지금 값 그대로. 표시 문구는 {@code {광고주}}·{@code {제공/대여}} 자리표시를 쓴다(빠져도 된다).
 */
@Service
public class TrialSettingsService {

    /** 저장 요청 — 빠진 칸(null)은 지금 값 그대로 */
    public record Request(Long givePriceLimit, String disclosureText, String sponsorDisclosureText, Integer returnRemindFirstDays, Integer returnRemindSecondDays,
                          Integer unreturnedDays, Integer reviewDays, Integer lateBanDays, Integer lateRevokeStrikes, Integer proofBonusPoints, Integer proofPhotoDays,
                          Integer stockKeepDays) {
    }

    private final TrialSettingRepository settings;
    private final AuditLogService audit;
    private final Clock clock;

    public TrialSettingsService(TrialSettingRepository settings, AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.settings = settings;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public Values current() {
        return settings.findById(TrialSettingEntity.ID).map(TrialSettingEntity::values).orElseThrow(() -> new IllegalStateException("trial_settings 행이 없다"));
    }

    @Transactional
    public Values update(long operatorId, String ip, Request body) {
        TrialSettingEntity row = settings.findWithLockById(TrialSettingEntity.ID).orElseThrow(() -> new IllegalStateException("trial_settings 행이 없다"));
        Values now = row.values();
        Request b = body == null ? new Request(null, null, null, null, null, null, null, null, null, null, null, null) : body;
        Values next = new Values(b.givePriceLimit() == null ? now.givePriceLimit() : b.givePriceLimit(), text(b.disclosureText(), now.disclosureText()),
                text(b.sponsorDisclosureText(), now.sponsorDisclosureText()), or(b.returnRemindFirstDays(), now.returnRemindFirstDays()),
                or(b.returnRemindSecondDays(), now.returnRemindSecondDays()), or(b.unreturnedDays(), now.unreturnedDays()), or(b.reviewDays(), now.reviewDays()),
                or(b.lateBanDays(), now.lateBanDays()), or(b.lateRevokeStrikes(), now.lateRevokeStrikes()), or(b.proofBonusPoints(), now.proofBonusPoints()),
                or(b.proofPhotoDays(), now.proofPhotoDays()), or(b.stockKeepDays(), now.stockKeepDays()));
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        range(fields, "givePriceLimit", next.givePriceLimit(), 0, 100_000_000);
        length(fields, "disclosureText", next.disclosureText());
        length(fields, "sponsorDisclosureText", next.sponsorDisclosureText());
        range(fields, "returnRemindFirstDays", next.returnRemindFirstDays(), 0, 60);
        range(fields, "returnRemindSecondDays", next.returnRemindSecondDays(), next.returnRemindFirstDays() + 1, 119);
        range(fields, "unreturnedDays", next.unreturnedDays(), next.returnRemindSecondDays() + 1, 120);
        range(fields, "reviewDays", next.reviewDays(), 1, 90);
        range(fields, "lateBanDays", next.lateBanDays(), 1, 3650);
        range(fields, "lateRevokeStrikes", next.lateRevokeStrikes(), 1, 10);
        range(fields, "proofBonusPoints", next.proofBonusPoints(), 0, 10_000);
        range(fields, "proofPhotoDays", next.proofPhotoDays(), 1, 365);
        range(fields, "stockKeepDays", next.stockKeepDays(), 1, 365);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        diff(before, after, "givePriceLimit", now.givePriceLimit(), next.givePriceLimit());
        diff(before, after, "disclosureText", now.disclosureText(), next.disclosureText());
        diff(before, after, "sponsorDisclosureText", now.sponsorDisclosureText(), next.sponsorDisclosureText());
        diff(before, after, "returnRemindFirstDays", now.returnRemindFirstDays(), next.returnRemindFirstDays());
        diff(before, after, "returnRemindSecondDays", now.returnRemindSecondDays(), next.returnRemindSecondDays());
        diff(before, after, "unreturnedDays", now.unreturnedDays(), next.unreturnedDays());
        diff(before, after, "reviewDays", now.reviewDays(), next.reviewDays());
        diff(before, after, "lateBanDays", now.lateBanDays(), next.lateBanDays());
        diff(before, after, "lateRevokeStrikes", now.lateRevokeStrikes(), next.lateRevokeStrikes());
        diff(before, after, "proofBonusPoints", now.proofBonusPoints(), next.proofBonusPoints());
        diff(before, after, "proofPhotoDays", now.proofPhotoDays(), next.proofPhotoDays());
        diff(before, after, "stockKeepDays", now.stockKeepDays(), next.stockKeepDays());
        if (before.isEmpty()) {
            return now;
        }
        row.change(next, operatorId, Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.TRIAL_SETTINGS_UPDATE, AuditResult.SUCCESS).target("TRIAL_SETTINGS", "1")
                .with("before", before).with("after", after));
        return next;
    }

    private static int or(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String text(String value, String fallback) {
        return value == null ? fallback : value.strip();
    }

    private static void range(Map<String, MessageRef> fields, String name, long value, long min, long max) {
        if (value < min || value > max) {
            fields.put(name, new MessageRef("trial.settings.range", String.valueOf(min), String.valueOf(max)));
        }
    }

    private static void length(Map<String, MessageRef> fields, String name, String value) {
        if (value == null || value.isBlank() || value.length() > 300) {
            fields.put(name, new MessageRef("trial.settings.text", "300"));
        }
    }

    private static void diff(Map<String, Object> before, Map<String, Object> after, String name, Object a, Object b) {
        if (!Objects.equals(a, b)) {
            before.put(name, a);
            after.put(name, b);
        }
    }
}
