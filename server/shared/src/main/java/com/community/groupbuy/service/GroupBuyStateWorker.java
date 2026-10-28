package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.entity.ExtensionMode;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.repository.master.GroupBuyQtyStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공구 상태 워커의 한 바퀴({@value #WORKER_KEY} — commerce.md 10-7·15-4·15-5, 계약 결정 9·25·27·37·50·78·81) — 「언제 돌릴지」(1분, 배치를 켠 관리자 서버만)는
 * 관리자 서버의 {@code GroupBuyStateScheduler}. 단계마다 공구 하나씩 <b>짧은 새 트랜잭션</b>(한 공구가 실패해도 나머지는 간다), 모두 조건부 전이라 두 번 돌아도 한 번만.
 *
 * <ol>
 *   <li>선점 만료(HOLD_TIMEOUT — {@link HoldExpiryService})</li>
 *   <li>모집 시작: 공개됐고 시작 시각이 된 DRAFT → RECRUITING({@link GroupBuyAdminService#startNow} — 배송비 사본·처음 수량·수정 이력 1)</li>
 *   <li>마감: 마감 시각이 지난 모집 중 공구 — 연장 조건(10-2: DEMAND = 결제 중 선점이 남았거나 마감 전 판단 창(5분) 안에 수량이 풀림(RELEASE·CANCEL·RESTOCK·ADD — 결정 25·81),
 *       ALWAYS = 수량이 남음)이면 원래 마감에서 한 번 {@code extended_until = end_at + 연장 시간}, 아니면 CLOSING(TIME_UP). 연장 끝이 지나면 CLOSING.</li>
 *   <li>다 팔림 안전망(10-7 ③ — 판매 중인 MAIN 단품 전부 다 팔림 포함, 결정 78)</li>
 *   <li>판정: 집계 중이고 (남은 선점 0 이거나 closing_at + 선점 시간 + 여유가 지남) — 공구 행을 {@code FOR UPDATE} 로 잠그고 다시 판단(결정 37 ①). 확정 ≥ 최소면 성사, 아니면 불발
 *       (불발 환불은 {@code groupbuy-refund} 워커 — 결정 61).</li>
 *   <li>성사 공구의 JOINED → CONFIRMED 일괄(판정 트랜잭션과 따로 — 공구 행을 쥔 채 주문 행을 잠그지 않는다)</li>
 *   <li>COMPLETED sweep(결정 9 — 진행 중 주문이 없는 즉시 발송 성사·SHIPPING 공구)</li>
 * </ol>
 */
@Component
public class GroupBuyStateWorker {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyStateWorker.class);

    /** 워커 기록({@code worker_heartbeats})의 키 */
    public static final String WORKER_KEY = "groupbuy-state";
    /** 단계마다 한 바퀴에 다루는 상한 */
    static final int STEP_LIMIT = 200;

    private final GroupBuyStore groups;
    private final GroupBuyQtyStore qtyStore;
    private final ParticipationStore participations;
    private final HoldExpiryService holds;
    private final GroupBuyAdminService admin;
    private final GroupBuyQtyService qty;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate separate;
    private final Clock clock;

    public GroupBuyStateWorker(GroupBuyStore groups, GroupBuyQtyStore qtyStore, ParticipationStore participations, HoldExpiryService holds, GroupBuyAdminService admin,
                               GroupBuyQtyService qty, SnowflakeIdGenerator ids, ApplicationEventPublisher events, PlatformTransactionManager transactions,
                               ObjectProvider<Clock> clocks) {
        this.groups = groups;
        this.qtyStore = qtyStore;
        this.participations = participations;
        this.holds = holds;
        this.admin = admin;
        this.qty = qty;
        this.ids = ids;
        this.events = events;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 한 바퀴 — 다룬 건수(워커 기록) */
    public int runOnce() {
        int handled = holds.expireOverdue(STEP_LIMIT);
        for (long id : groups.dueToStart(now(), STEP_LIMIT)) {
            handled += step("start", id, () -> admin.startNow(id));
        }
        for (long id : groups.recruitingPastDeadline(now(), STEP_LIMIT)) {
            handled += step("close", id, () -> closeOrExtend(id));
        }
        for (long id : groups.recruitingSoldOut(STEP_LIMIT)) {
            handled += step("soldOut", id, () -> Boolean.TRUE.equals(separate.execute(s -> qty.closeIfSoldOut(id, null, now()))));
        }
        GroupBuyStore.Settings settings = groups.settings();
        for (long id : groups.closingDue(now(), settings.holdMinutes(), settings.judgeGraceMinutes(), STEP_LIMIT)) {
            handled += step("judge", id, () -> judge(id));
        }
        handled += confirmSucceeded();
        handled += completeFinished();
        return handled;
    }

    /**
     * 마감 시각이 지난 모집 중 공구 하나 — 연장하거나 CLOSING. 바꿨으면 true.
     */
    public boolean closeOrExtend(long groupBuyId) {
        Boolean changed = separate.execute(status -> {
            Instant now = now();
            Optional<GroupBuyRow> found = groups.find(groupBuyId);
            if (found.isEmpty() || found.get().status() != GroupBuyStatus.RECRUITING || now.isBefore(found.get().deadline())) {
                return false;
            }
            GroupBuyRow g = found.get();
            GroupBuyStore.Settings settings = groups.settings();
            if (g.extendedUntil() == null && settings.extensionMinutes() > 0 && wantsExtension(g, settings, now)) {
                Instant until = g.endAt().plus(Duration.ofMinutes(settings.extensionMinutes()));
                if (now.isBefore(until) && groups.extend(g.id(), until, now)) {
                    groups.insertStatusHistory(ids.nextId(), g.id(), GroupBuyStatus.RECRUITING, GroupBuyStatus.RECRUITING, "EXTENDED", null, now);
                    events.publishEvent(new GroupBuyEvents.Extended(g.id(), until));
                    return true;
                }
            }
            if (groups.closeTimeUp(g.id(), now)) {
                groups.insertStatusHistory(ids.nextId(), g.id(), GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING, "TIME_UP", null, now);
                events.publishEvent(new GroupBuyEvents.StatusChanged(g.id(), GroupBuyStatus.RECRUITING.name(), GroupBuyStatus.CLOSING.name(), "TIME_UP"));
                return true;
            }
            return false;
        });
        return Boolean.TRUE.equals(changed);
    }

    /** 연장 조건(10-2 표 · 결정 25·81) — 판단은 원래 마감 시각 기준 창 [end_at − 창, 지금] */
    boolean wantsExtension(GroupBuyRow g, GroupBuyStore.Settings settings, Instant now) {
        if (g.currentQty() >= g.maxQty()) {
            return false;
        }
        if (ExtensionMode.ALWAYS.name().equals(g.extensionMode())) {
            return true;
        }
        if (g.reservedQty() > 0) {
            return true; // 마감 시각에 결제 중인 선점이 남았다
        }
        return qtyStore.releasedBetween(g.id(), g.endAt().minus(Duration.ofMinutes(settings.extensionWindowMinutes())), now);
    }

    /**
     * 판정 ① — 공구 행을 잠그고 다시 판단한 뒤 전이(결정 37). 판정할 때가 아니면 false. 성사형은 확정 ≥ 최소 수량이면 성사, 선착순은 늘 성사.
     * 성사 뒤 주문 확정 일괄·불발 환불은 다른 트랜잭션(결정 37·61).
     */
    public boolean judge(long groupBuyId) {
        Boolean judged = separate.execute(status -> {
            Instant now = now();
            Optional<GroupBuyRow> locked = groups.lock(groupBuyId);
            if (locked.isEmpty() || locked.get().status() != GroupBuyStatus.CLOSING) {
                return false;
            }
            GroupBuyRow g = locked.get();
            GroupBuyStore.Settings settings = groups.settings();
            Instant due = g.closingAt().plus(Duration.ofMinutes(settings.holdMinutes() + settings.judgeGraceMinutes()));
            if (g.reservedQty() > 0 && now.isBefore(due)) {
                return false; // 마감 전에 잡힌 선점이 아직 결제 중이다(10-2 — 결제를 받는다)
            }
            boolean success = g.minQty() == 0 || g.currentQty() >= g.minQty();
            if (!groups.judge(g.id(), success, now)) {
                return false;
            }
            GroupBuyStatus to = success ? GroupBuyStatus.CLOSED_SUCCESS : GroupBuyStatus.CLOSED_FAILED;
            groups.insertStatusHistory(ids.nextId(), g.id(), GroupBuyStatus.CLOSING, to, success ? "JUDGED_SUCCESS" : "JUDGED_FAILED", null, now);
            events.publishEvent(new GroupBuyEvents.StatusChanged(g.id(), GroupBuyStatus.CLOSING.name(), to.name(), null));
            return true;
        });
        return Boolean.TRUE.equals(judged);
    }

    /** 성사 공구의 JOINED → CONFIRMED 일괄(+ 상태 이력) — 바꾼 수 */
    int confirmSucceeded() {
        Integer n = separate.execute(status -> {
            Instant now = now();
            List<long[]> changed = participations.confirmJoinedOfSucceeded(now, 500);
            for (long[] row : changed) {
                participations.insertHistory(ids.nextId(), row[0], row[1], row[2], ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED, "JUDGED_SUCCESS", now);
            }
            return changed.size();
        });
        return n == null ? 0 : n;
    }

    /** COMPLETED sweep(결정 9) */
    int completeFinished() {
        Integer n = separate.execute(status -> {
            Instant now = now();
            List<Long> done = groups.completeFinished(now);
            for (long id : done) {
                groups.insertStatusHistory(ids.nextId(), id, null, GroupBuyStatus.COMPLETED, "ALL_ORDERS_DONE", null, now);
            }
            return done.size();
        });
        return n == null ? 0 : n;
    }

    private int step(String name, long id, java.util.function.BooleanSupplier work) {
        try {
            return work.getAsBoolean() ? 1 : 0;
        } catch (RuntimeException e) {
            log.error("공구 상태 워커 단계가 실패했다 step={} groupBuyId={} 원인={}", name, id, e.getClass().getSimpleName());
            return 0;
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
