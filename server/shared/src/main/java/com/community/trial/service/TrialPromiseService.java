package com.community.trial.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.entity.AuditActorType;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.restriction.entity.RestrictionAutoRule;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.title.service.BuiltinTitles;
import com.community.title.service.TitleGrantService;
import com.community.trial.entity.TrialDamageReportEntity;
import com.community.trial.entity.TrialDamageVerdict;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialSettingEntity;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험단 약속·불이익(trial-group-contract 「미반납」·「대여형 파손」·「구현 결정」 3·11·12) — 부르는 쪽 트랜잭션에 참여한다.
 *
 * <ul>
 *   <li><b>약속 지킴</b>: 사용기 기한 안에 연결 사용기 + 대여형이면 반납까지 → {@code kept = true}, 리뷰어 칭호를 가졌으면 지킨 횟수로 단계를 올린다(3·10·30 → 동·은·금).</li>
 *   <li><b>사용기 기한 넘김</b>: {@code kept = false} · 체험단 신청 금지 {@code late_ban_days}(기본 90) · 알림 {@code TRIAL_REVIEW_DUE} · 넘김이 {@code late_revoke_strikes}(기본 2)번째면
 *       리뷰어 칭호 회수(사유 {@code TRIAL_REVIEW_LATE}).</li>
 *   <li><b>미반납 확정</b>: {@code kept = false} · 영구 신청 금지 · 리뷰어 칭호 회수 · 광고주에게 이름·연락처 제공 기록(감사 {@code TRIAL_PII_PROVIDE}) · 알림 {@code TRIAL_UNRETURNED}.</li>
 *   <li><b>파손 판정</b>: 회원 과실 → 신청 90일 금지 + 이름·연락처 제공 · 고의·숨김 반납 → 영구 금지 + 리뷰어 회수 + 제공. 정상 마모·택배 파손은 불이익 없음.</li>
 * </ul>
 */
@Service
public class TrialPromiseService {

    /** 회원 과실 파손의 신청 금지 일수(계약 판정표 — 고정) */
    public static final int DAMAGE_FAULT_BAN_DAYS = 90;

    private final TrialParticipantRepository participants;
    private final MemberRestrictionService restrictions;
    private final TitleGrantService titles;
    private final MemberNotificationPublisher notices;
    private final AuditLogService audit;
    private final Messages messages;

    public TrialPromiseService(TrialParticipantRepository participants, MemberRestrictionService restrictions, TitleGrantService titles,
                               MemberNotificationPublisher notices, AuditLogService audit, Messages messages) {
        this.participants = participants;
        this.restrictions = restrictions;
        this.titles = titles;
        this.notices = notices;
        this.audit = audit;
        this.messages = messages;
    }

    /** 약속을 다 지켰으면 판정하고 리뷰어 단계를 올린다 — 사용기 연결·반납 받음 뒤에 부른다. 이미 판정했으면 그대로 */
    @Transactional
    public void judgeIfDone(TrialParticipantEntity p, TrialEntity t, Instant now) {
        if (p.getKept() != null || p.getReviewPostId() == null) {
            return;
        }
        boolean inTime = p.getReviewDueAt() == null || !p.getReviewPostedAt().isAfter(p.getReviewDueAt());
        if (!inTime) {
            return; // 기한 넘김은 배치가 판정한다(넘긴 뒤 연결한 글은 세지 않는다)
        }
        if (t.lend() && !p.returned()) {
            return; // 대여형은 반납까지
        }
        p.judge(true, now);
        participants.flush();
        raiseReviewer(p.getUserId());
    }

    /** 리뷰어 칭호를 가졌으면 약속 지킨 횟수에 맞게 단계를 올린다 */
    public void raiseReviewer(long userId) {
        titles.raiseTierIfHeld(BuiltinTitles.REVIEWER, userId, participants.countByUserIdAndKeptTrue(userId));
    }

    /** 사용기 기한 넘김(배치) */
    @Transactional
    public void reviewLate(TrialParticipantEntity p, TrialEntity t, TrialSettingEntity.Values settings, Instant now) {
        p.markReviewLate(now);
        if (p.getKept() == null) {
            p.judge(false, now);
        }
        participants.flush();
        restrictions.grantAuto(p.getUserId(), RestrictionType.TRIAL_BAN, Duration.ofDays(settings.lateBanDays()), RestrictionAutoRule.TRIAL_REVIEW_LATE);
        long strikes = participants.countByUserIdAndReviewLateAtIsNotNull(p.getUserId());
        if (strikes >= settings.lateRevokeStrikes()) {
            titles.revokeBySystem(BuiltinTitles.REVIEWER, p.getUserId(), RestrictionAutoRule.TRIAL_REVIEW_LATE);
        }
        notices.trial(p.getUserId(), MemberNotificationType.TRIAL_REVIEW_DUE, t.getTitle(),
                messages.get(new MessageRef("trial.notice.reviewLate", String.valueOf(settings.lateBanDays()))), "/me/trials");
    }

    /** 미반납 확정(배치) */
    @Transactional
    public void unreturned(TrialParticipantEntity p, TrialEntity t, Instant now) {
        p.markUnreturned(now);
        if (p.getKept() == null) {
            p.judge(false, now);
        }
        p.markContactProvided(now);
        participants.flush();
        restrictions.grantAutoIndefinite(p.getUserId(), RestrictionType.TRIAL_BAN, RestrictionAutoRule.TRIAL_UNRETURNED);
        titles.revokeBySystem(BuiltinTitles.REVIEWER, p.getUserId(), RestrictionAutoRule.TRIAL_UNRETURNED);
        audit.record(system(AuditActions.TRIAL_UNRETURNED, "MEMBER", Long.toString(p.getUserId())).with("trialId", Long.toString(t.getId()))
                .with("participantId", Long.toString(p.getId())));
        recordProvision(t, p, List.of("NAME", "PHONE"), "UNRETURNED", null);
        notices.trial(p.getUserId(), MemberNotificationType.TRIAL_UNRETURNED, t.getTitle(), messages.get(new MessageRef("trial.notice.unreturned")), "/me/trials");
    }

    /** 파손 판정의 불이익 — 과실·고의면 이름·연락처 제공 기록까지 */
    @Transactional
    public void damageVerdict(TrialDamageReportEntity report, TrialParticipantEntity p, TrialEntity t, TrialDamageVerdict verdict, long operatorId, String ip, Instant now) {
        switch (verdict) {
            case MEMBER_FAULT -> restrictions.grantAuto(p.getUserId(), RestrictionType.TRIAL_BAN, Duration.ofDays(DAMAGE_FAULT_BAN_DAYS),
                    RestrictionAutoRule.TRIAL_DAMAGE_FAULT);
            case INTENTIONAL -> {
                restrictions.grantAutoIndefinite(p.getUserId(), RestrictionType.TRIAL_BAN, RestrictionAutoRule.TRIAL_DAMAGE_INTENTIONAL);
                titles.revokeBySystem(BuiltinTitles.REVIEWER, p.getUserId(), RestrictionAutoRule.TRIAL_DAMAGE_INTENTIONAL);
            }
            default -> {
                return; // 정상 마모 · 택배 파손 — 청구·불이익 없음
            }
        }
        report.markContactProvided(now);
        p.markContactProvided(now);
        recordProvision(t, p, List.of("NAME", "PHONE", "VERDICT", "PHOTOS"), "DAMAGE_" + verdict.name(), operatorId);
    }

    /** 개인정보 제공(광고주에게) 감사 기록 — 개인정보 보관 분류. 운영자 없음 = 시스템 */
    public void recordProvision(TrialEntity t, TrialParticipantEntity p, List<String> fields, String cause, Long operatorId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("advertiserId", Long.toString(t.getAdvertiserId()));
        detail.put("participantId", Long.toString(p.getId()));
        detail.put("memberId", Long.toString(p.getUserId()));
        detail.put("fields", fields);
        detail.put("cause", cause);
        AuditEvent event = operatorId == null ? system(AuditActions.TRIAL_PII_PROVIDE, "TRIAL", Long.toString(t.getId()))
                : AuditEvent.byOperator(operatorId, null, AuditActions.TRIAL_PII_PROVIDE, AuditResult.SUCCESS).target("TRIAL", Long.toString(t.getId()));
        for (Map.Entry<String, Object> e : detail.entrySet()) {
            event = event.with(e.getKey(), e.getValue());
        }
        audit.record(event);
    }

    private static AuditEvent system(String action, String targetType, String targetId) {
        return new AuditEvent(AuditActorType.SYSTEM, null, action, AuditResult.SUCCESS, targetType, targetId, null, Map.of());
    }
}
