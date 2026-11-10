package com.community.rewardevent.service;

import com.community.audit.dto.AuditActions;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventLineResultEntity;
import com.community.rewardevent.entity.RewardEventRecipientEntity;
import com.community.rewardevent.entity.RewardEventTrigger;
import com.community.rewardevent.entity.RewardRecipientMethod;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import com.community.rewardevent.repository.master.RewardEventStore.TierRow;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardMemberQueries.MemberRow;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientRow;
import com.community.rewardevent.service.RewardLineGranter.EventContext;
import com.community.rewardevent.service.RewardLineGranter.Granted;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 선착순 자동 지급(event-admin-contract 「상태·방식」 FIRST_COME) — 기준 사건(본인인증 확정 · 이메일 인증 완료)이 일어난 <b>그 트랜잭션 안에서</b> 부른다.
 *
 * <ul>
 *   <li>진행 중이고 기간 안이고 자리가 남은, 그 기준 사건의 모든 선착순 이벤트에 대해 이벤트마다 세이브포인트({@link IsolatedSideWork}) — <b>실패해도 인증은 성공한다</b>(가입 포인트와 같다).</li>
 *   <li>정상 회원만(정지·탈퇴 제외), 운영자 회원 계정은 {@code excludeOperators} 일 때 제외, 이벤트당 1인 1회(이미 받은 사람은 그대로).</li>
 *   <li>자리: 「한 문장 조건부 증가」({@link RewardEventStore#takeFirstComeSlot}) — 같은 이벤트의 동시 인증은 이벤트 행에서 줄을 서고 인원을 넘지 않는다. 자리를 잡은 뒤 받는 사람·줄 결과를
 *       넣고 줄을 지급한다(줄마다 세이브포인트). 인원이 차면 그 자리에서 마감(감사 {@code REWARD_EVENT_AUTO_CLOSE} cause FULL) — 대기가 없으면 지급 끝.</li>
 *   <li>지급 내역은 날마다 한 줄(AUTO, 표시 시간대 날짜)에 더한다.</li>
 *   <li>세이브포인트 안에서 영속성 문맥에 남는 엔티티가 없다 — 넣기는 내보낸 뒤 떼고, 고치기는 DML 이다(되돌린 뒤 내보내질 변경이 없게).</li>
 * </ul>
 */
@Service
public class RewardFirstComeService {

    private static final Logger log = LoggerFactory.getLogger(RewardFirstComeService.class);

    private final RewardEventStore events;
    private final RewardRecipientStore recipients;
    private final RewardMemberQueries members;
    private final RewardLineGranter granter;
    private final RewardEventSupport support;
    private final OperatorMemberAccountService operatorAccounts;
    private final IsolatedSideWork isolated;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public RewardFirstComeService(RewardEventStore events, RewardRecipientStore recipients, RewardMemberQueries members, RewardLineGranter granter,
                                  RewardEventSupport support, OperatorMemberAccountService operatorAccounts, IsolatedSideWork isolated, SnowflakeIdGenerator ids,
                                  ObjectProvider<Clock> clocks) {
        this.events = events;
        this.recipients = recipients;
        this.members = members;
        this.granter = granter;
        this.support = support;
        this.operatorAccounts = operatorAccounts;
        this.isolated = isolated;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 본인인증을 확정했다(그 트랜잭션 안) — 받은 이벤트 수 */
    public int onIdentityVerified(long userId) {
        return on(RewardEventTrigger.IDENTITY_VERIFIED, userId);
    }

    /** 이메일 인증을 처음 마쳤다(가입 완료 — 그 트랜잭션 안) — 받은 이벤트 수 */
    public int onEmailVerified(long userId) {
        return on(RewardEventTrigger.EMAIL_VERIFIED, userId);
    }

    private int on(RewardEventTrigger trigger, long userId) {
        List<Long> open = isolated.run("reward-event:first-come-find", () -> events.openFirstCome(trigger, now()), List.of());
        int got = 0;
        for (Long eventId : open) {
            if (isolated.run("reward-event:first-come", () -> grantOne(eventId, userId), false)) {
                got++;
            }
        }
        return got;
    }

    /** 이벤트 하나 — 자리를 잡고 받는 사람을 넣고 줄을 지급한다. 받았으면 true */
    private boolean grantOne(long eventId, long userId) {
        Instant now = now();
        Optional<MemberRow> member = members.member(userId);
        if (member.isEmpty() || !member.get().active() || members.aiUser(userId)) {
            return false; // AI 회원은 선착순 대상이 아니다(ai-members-contract 정책 10)
        }
        RewardEventEntity e = events.event(eventId).orElse(null);
        if (e == null || e.isExcludeOperators() && operatorAccounts.isActiveOperatorAccount(userId)) {
            return false;
        }
        if (recipients.exists(eventId, userId)) {
            return false; // 이벤트당 1인 1회
        }
        if (!events.takeFirstComeSlot(eventId, now)) {
            return false; // 인원이 찼거나 기간 밖·진행 중 아님
        }
        if (recipients.exists(eventId, userId)) {
            events.releaseFirstComeSlot(eventId); // 자리를 기다리는 사이 같은 회원이 먼저 받았다(이벤트 행 잠금 뒤 다시 본다)
            return false;
        }
        TierRow tier = events.tiers(eventId).get(0);
        List<LineRow> lines = events.lines(eventId);
        long rid = ids.nextId();
        List<RewardEventLineResultEntity> results = new ArrayList<>();
        for (LineRow l : lines) {
            results.add(RewardEventLineResultEntity.pending(ids.nextId(), eventId, rid, l.id(), userId, l.kind()));
        }
        recipients.insert(List.of(RewardEventRecipientEntity.pending(rid, eventId, tier.id(), userId, RewardRecipientMethod.FIRST_COME, now, null, null, null)), results);
        RecipientRow row = recipients.recipient(rid).orElseThrow();
        Map<Long, LineRow> byId = lines.stream().collect(Collectors.toMap(LineRow::id, Function.identity()));
        Granted g = granter.grant(new EventContext(eventId, e.getName(), null), row, byId, false, now);
        long runId = events.autoRunOf(eventId, now.atZone(ServiceTime.ZONE).toLocalDate(), ids.nextId(), now);
        events.addAuto(runId, g.success(), g.failed(), g.skipped(), g.points(), g.items(), now);
        if (events.closeFull(eventId, now)) {
            support.settleDone(eventId, now);
            // 감사(JPA 넣기)는 이 세이브포인트의 마지막 문장 — 뒤에서 실패해 되돌리면 넣지 않은 기록이 문맥에 남아 나중에 내보내질 수 있다
            support.record(null, null, AuditActions.REWARD_EVENT_AUTO_CLOSE, eventId, Map.of("cause", "FULL", "limit", e.getFirstComeLimit()));
        }
        log.info("선착순 보상 지급 eventId={} userId={} status={}", eventId, userId, g.status());
        return true;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
