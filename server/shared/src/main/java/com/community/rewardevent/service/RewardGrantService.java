package com.community.rewardevent.service;

import com.community.audit.dto.AuditActions;
import com.community.rewardevent.dto.RewardOperator;
import com.community.rewardevent.dto.response.RewardGrantPreviewView;
import com.community.rewardevent.dto.response.RewardRecipientView;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.entity.RewardBulkScope;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventLineResultEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventRecipientEntity;
import com.community.rewardevent.entity.RewardEventRunEntity;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardRecipientMethod;
import com.community.rewardevent.entity.RewardRecipientStatus;
import com.community.rewardevent.entity.RewardRunKind;
import com.community.rewardevent.entity.RewardRunPhase;
import com.community.rewardevent.entity.RewardRunStatus;
import com.community.rewardevent.exception.RewardEventConflictException;
import com.community.rewardevent.exception.RewardEventNotFoundException;
import com.community.rewardevent.exception.RewardLargeRequiredException;
import com.community.rewardevent.exception.RewardRecipientNotFoundException;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import com.community.rewardevent.repository.master.RewardEventStore.TierRow;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientRow;
import com.community.rewardevent.repository.master.RewardRecipientStore.ResultRow;
import com.community.rewardevent.service.RewardLineGranter.EventContext;
import com.community.rewardevent.service.RewardLineGranter.Granted;
import com.community.rewardevent.service.RewardLineGranter.Revoked;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지급·실패만 다시 지급·회수·직접 전달 완료(event-admin-contract 「보상 줄」·「알림·감사」 — 커머스).
 *
 * <ul>
 *   <li><b>지급 실행</b>({@link #grant}): 멱등 키(이벤트마다 유일 — 같은 키는 처음 실행을 돌려준다). 당첨자 선정은 마감 뒤 대기 중인 받는 사람을, 일괄은 진행 중·마감에서 한 번(진행 중이면 마감으로
 *       바꾸고) 그 순간의 조건으로 대상을 정한다. 고액(1인 포인트 합 &gt; 문턱 · 일괄 포인트)은 {@code point.adjust.large} — 없으면 거부 감사 + 403.</li>
 *   <li><b>나눠 처리</b>: 실행 한 번 = {@code RUNNING} 한 줄. 한 덩어리 = 한 트랜잭션({@link #processChunk}) — 실행 행을 {@code SKIP LOCKED} 로 잡은 곳만 처리하므로 서버 여러 대여도
 *       한 곳만 하고, 서버가 죽어도 잠금이 풀려 남은 덩어리를 이어 한다(대기열 = 받는 사람의 {@code queued_run_id}, 일괄 대상 만들기는 회원 번호 cursor). 요청은 몇 덩어리를 바로 처리하고
 *       남은 것은 워커({@link RewardRunWorker})가 이어 한다.</li>
 *   <li>한 사람의 줄은 {@link RewardLineGranter} — 줄마다 세이브포인트(한 줄의 실패가 다른 줄을 되돌리지 않는다).</li>
 *   <li><b>회수</b>({@link #revoke}): 지급 완료·일부 실패한 사람만, 한 요청 500명까지 한 번에(실행 REVOKE — 끝난 줄). 사유 5~200자(회원 알림에 보인다).</li>
 * </ul>
 */
@Service
public class RewardGrantService {

    private static final Logger log = LoggerFactory.getLogger(RewardGrantService.class);

    /** 일괄 대상 만들기 한 덩어리(회원 수) */
    static final int TARGET_CHUNK = 500;
    /** 보상 지급 한 덩어리(받는 사람 수) */
    static final int LINE_CHUNK = 100;
    /** 요청 안에서 바로 처리하는 덩어리 수(남은 것은 워커가) */
    static final int INLINE_CHUNKS = 3;

    private final RewardEventStore events;
    private final RewardRecipientStore recipients;
    private final RewardMemberQueries members;
    private final RewardLineGranter granter;
    private final RewardEventSupport support;
    private final RewardEventQueryService queries;
    private final RewardEventViews views;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final Clock clock;
    private int targetChunk = TARGET_CHUNK;
    private int lineChunk = LINE_CHUNK;
    private int inlineChunks = INLINE_CHUNKS;

    public RewardGrantService(RewardEventStore events, RewardRecipientStore recipients, RewardMemberQueries members, RewardLineGranter granter, RewardEventSupport support,
                              RewardEventQueryService queries, RewardEventViews views, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                              ObjectProvider<Clock> clocks) {
        this.events = events;
        this.recipients = recipients;
        this.members = members;
        this.granter = granter;
        this.support = support;
        this.queries = queries;
        this.views = views;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 시험이 덩어리 크기·요청 안 덩어리 수를 줄인다(나눠 처리·이어 하기를 적은 회원으로 보려고) */
    void chunks(int target, int lines, int inline) {
        this.targetChunk = target;
        this.lineChunk = lines;
        this.inlineChunks = inline;
    }

    // ── 지급 · 재시도 ───────────────────────────────────────────────────────

    /** 지급 실행 — 실행을 만들고(한 트랜잭션) 몇 덩어리를 바로 처리한 뒤 그 실행을 돌려준다(남은 것은 워커가) */
    public RewardRunView grant(RewardOperator op, long eventId, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.strip();
        if (key.isEmpty() || key.length() > RewardEventRules.IDEMPOTENCY_KEY_MAX) {
            throw ValidationException.of("idempotencyKey", new MessageRef("rewardEvent.idempotencyKey.invalid", RewardEventRules.IDEMPOTENCY_KEY_MAX));
        }
        long runId;
        try {
            runId = tx.execute(status -> startGrant(op, eventId, key));
        } catch (DuplicateKeyException e) {
            // 같은 키(또는 같은 이벤트의 다른 실행)가 동시에 — 처음 것을 돌려준다
            runId = tx.execute(status -> events.runByKey(eventId, key).map(RewardEventRunEntity::getId)
                    .orElseThrow(() -> new RewardEventConflictException(RewardEventConflictException.STATE)));
        }
        drain(runId, inlineChunks);
        return runView(runId);
    }

    private long startGrant(RewardOperator op, long eventId, String key) {
        Instant now = now();
        RewardEventEntity e = support.lockFresh(eventId, now);
        Optional<RewardEventRunEntity> prior = events.runByKey(eventId, key);
        if (prior.isPresent()) {
            return prior.get().getId();
        }
        boolean running = events.runningRun(eventId).isPresent();
        boolean granted = !events.grantedEvents(List.of(eventId)).isEmpty();
        boolean ok = switch (e.getMode()) {
            case SELECTION -> e.getStatus() == RewardEventStatus.CLOSED && !running && recipients.countByStatus(eventId, RewardRecipientStatus.PENDING) > 0;
            case BULK -> (e.getStatus() == RewardEventStatus.ACTIVE || e.getStatus() == RewardEventStatus.CLOSED) && !running && !granted;
            case FIRST_COME -> false;
        };
        if (!ok) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        RewardGrantPreviewView preview = queries.preview(e, now);
        requireLarge(op, preview.largeRequired(), preview.largeThreshold());
        long runId = ids.nextId();
        if (e.getMode() == RewardEventMode.BULK) {
            events.insertRun(RewardEventRunEntity.running(runId, eventId, RewardRunKind.GRANT, RewardRunPhase.TARGETS, op.id(), key, (int) preview.targetCount(), now));
            events.closeForGrant(eventId, now); // 진행 중이었으면 마감 — 대상은 지금의 조건으로 정한다
        } else {
            events.insertRun(RewardEventRunEntity.running(runId, eventId, RewardRunKind.GRANT, RewardRunPhase.LINES, op.id(), key, 0, now));
            int queued = recipients.enqueue(eventId, runId, EnumSet.of(RewardRecipientStatus.PENDING));
            events.targetsDone(runId, queued, now);
        }
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_GRANT, eventId, Map.of("runId", Long.toString(runId), "phase", "START",
                "targetCount", preview.targetCount(), "large", preview.largeRequired(), "idempotencyKey", key));
        return runId;
    }

    /** 실패만 다시 지급 — 실패·일부 실패한 사람의 실패 줄만(성공 줄은 결과 칸·유일 키가 막는다). 지급 끝(DONE)에서도 된다 */
    public RewardRunView retry(RewardOperator op, long eventId) {
        long runId = tx.execute(status -> startRetry(op, eventId));
        drain(runId, inlineChunks);
        return runView(runId);
    }

    private long startRetry(RewardOperator op, long eventId) {
        Instant now = now();
        RewardEventEntity e = support.lockFresh(eventId, now);
        if (e.getStatus() == RewardEventStatus.CANCELED || events.runningRun(eventId).isPresent()
                || recipients.countByStatus(eventId, RewardRecipientStatus.FAILED, RewardRecipientStatus.PARTIAL) == 0) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        Map<Long, Long> perTier = RewardEventRules.pointsByTier(events.lines(eventId));
        Map<Long, Long> failingTiers = new LinkedHashMap<>(recipients.countByTier(eventId, RewardRecipientStatus.FAILED));
        recipients.countByTier(eventId, RewardRecipientStatus.PARTIAL).forEach((k, v) -> failingTiers.merge(k, v, Long::sum));
        int threshold = queries.preview(e, now).largeThreshold();
        requireLarge(op, RewardEventRules.largeRequired(e.getMode(), failingTiers.keySet().stream().map(t -> perTier.getOrDefault(t, 0L)).toList(), threshold), threshold);
        long runId = ids.nextId();
        events.insertRun(RewardEventRunEntity.running(runId, eventId, RewardRunKind.RETRY, RewardRunPhase.LINES, op.id(), null, 0, now));
        int queued = recipients.enqueue(eventId, runId, EnumSet.of(RewardRecipientStatus.FAILED, RewardRecipientStatus.PARTIAL));
        events.targetsDone(runId, queued, now);
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_RETRY, eventId, Map.of("runId", Long.toString(runId), "phase", "START", "targetCount", queued));
        return runId;
    }

    private static void requireLarge(RewardOperator op, boolean large, int threshold) {
        if (large && !op.canLarge()) {
            op.recordLargeDenied().run();
            throw new RewardLargeRequiredException(threshold);
        }
    }

    /** 그 실행을 덩어리 {@code max} 개까지 처리한다(다른 곳이 잡고 있으면 바로 끝) — 처리한 덩어리 수 */
    public int drain(long runId, int max) {
        int done = 0;
        while (done < max && Boolean.TRUE.equals(tx.execute(status -> processChunk(runId)))) {
            done++;
        }
        return done;
    }

    /**
     * 덩어리 하나(한 트랜잭션 — 부르는 쪽이 연다): 실행 행을 {@code SKIP LOCKED} 로 잡고(못 잡으면 false), 일괄 대상 만들기 단계면 회원 번호 cursor 뒤로 {@value #TARGET_CHUNK}명의 대상 줄을
     * 만들고, 지급 단계면 대기열의 다음 {@value #LINE_CHUNK}명을 지급한다. 대기열이 비면 실행을 끝내고(감사 phase DONE) 지급 끝을 판정한다.
     *
     * @return 일을 했고 더 할 것이 있을 수 있으면 true
     */
    public boolean processChunk(long runId) {
        if (!events.claimRun(runId)) {
            return false;
        }
        Instant now = now();
        RewardEventRunEntity run = events.run(runId).orElseThrow();
        RewardEventEntity e = events.event(run.getEventId()).orElseThrow(RewardEventNotFoundException::new);
        if (run.getPhase() == RewardRunPhase.TARGETS) {
            TierRow tier = events.tiers(e.getId()).get(0);
            List<LineRow> lines = events.lines(e.getId());
            long after = run.getCursorUserId() == null ? 0 : run.getCursorUserId();
            List<Long> users = members.bulkTargets(RewardEventQueryService.condition(e), queries.excludedOperators(e.isExcludeOperators()), e.getId(), after, targetChunk,
                    now);
            RewardRecipientMethod method = e.getBulkScope() == RewardBulkScope.ALL ? RewardRecipientMethod.ALL : RewardRecipientMethod.CONDITION;
            List<RewardEventRecipientEntity> rows = new ArrayList<>();
            List<RewardEventLineResultEntity> results = new ArrayList<>();
            for (Long u : users) {
                long rid = ids.nextId();
                rows.add(RewardEventRecipientEntity.pending(rid, e.getId(), tier.id(), u, method, now, null, null, runId));
                for (LineRow l : lines) {
                    results.add(RewardEventLineResultEntity.pending(ids.nextId(), e.getId(), rid, l.id(), u, l.kind()));
                }
            }
            recipients.insert(rows, results);
            if (!users.isEmpty()) {
                events.advanceTargets(runId, users.get(users.size() - 1), now);
            }
            if (users.size() < targetChunk) {
                events.targetsDone(runId, (int) recipients.countQueued(runId), now);
            }
            return true;
        }
        List<RecipientRow> chunk = recipients.queued(runId, lineChunk);
        if (chunk.isEmpty()) {
            finish(e, events.run(runId).orElseThrow(), now);
            return false;
        }
        Map<Long, LineRow> lines = events.lines(e.getId()).stream().collect(Collectors.toMap(LineRow::id, Function.identity()));
        EventContext ctx = new EventContext(e.getId(), e.getName(), run.getOperatorId());
        int success = 0;
        int failed = 0;
        int skipped = 0;
        long points = 0;
        int items = 0;
        for (RecipientRow r : chunk) {
            Granted g = granter.grant(ctx, r, lines, run.getKind() == RewardRunKind.RETRY, now);
            success += g.success();
            failed += g.failed();
            skipped += g.skipped();
            points += g.points();
            items += g.items();
        }
        events.addProgress(runId, chunk.size(), success, failed, skipped, points, items, now);
        return true;
    }

    private void finish(RewardEventEntity e, RewardEventRunEntity run, Instant now) {
        if (!events.finishRun(run.getId(), now)) {
            return;
        }
        String action = run.getKind() == RewardRunKind.RETRY ? AuditActions.REWARD_EVENT_RETRY : AuditActions.REWARD_EVENT_GRANT;
        support.record(run.getOperatorId(), null, action, e.getId(), Map.of("runId", Long.toString(run.getId()), "phase", "DONE", "targetCount", run.getTargetCount(),
                "success", run.getSuccess(), "failed", run.getFailed(), "skipped", run.getSkipped(), "points", run.getPoints(), "items", run.getItems()));
        support.settleDone(e.getId(), now);
        log.info("보상 지급 실행 끝 eventId={} runId={} kind={} 대상={} 성공={} 실패={} 건너뜀={}", e.getId(), run.getId(), run.getKind(), run.getTargetCount(), run.getSuccess(),
                run.getFailed(), run.getSkipped());
    }

    public RewardRunView runView(long runId) {
        return tx.execute(status -> views.runs(List.of(events.run(runId).orElseThrow())).get(0));
    }

    // ── 회수 · 직접 전달 ────────────────────────────────────────────────────

    /** 회수 — 지급 완료·일부 실패한 사람만(없으면 {@code 409 REWARD_REVOKE_NOTHING}), 사유 5~200자, 500명까지. 끝난 실행(REVOKE) 한 줄 */
    public RewardRunView revoke(RewardOperator op, long eventId, List<String> recipientIds, String reason) {
        String text = RewardEventRules.normalize(reason);
        if (RewardEventRules.length(text) < RewardEventRules.REASON_MIN || RewardEventRules.length(text) > RewardEventRules.REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("rewardEvent.reason.length", RewardEventRules.REASON_MIN, RewardEventRules.REASON_MAX));
        }
        Set<Long> idSet = new LinkedHashSet<>((recipientIds == null ? List.<String>of() : recipientIds).stream().map(RewardEventRules::parseId).flatMap(Optional::stream).toList());
        if (idSet.isEmpty() || idSet.size() > RewardEventRules.BATCH_MAX) {
            throw ValidationException.of("recipientIds", new MessageRef("rewardEvent.recipientIds.range", RewardEventRules.BATCH_MAX));
        }
        long runId = tx.execute(status -> {
            Instant now = now();
            RewardEventEntity e = support.lockFresh(eventId, now);
            if (e.getStatus() == RewardEventStatus.CANCELED || events.runningRun(eventId).isPresent()) {
                throw new RewardEventConflictException(RewardEventConflictException.STATE);
            }
            List<RecipientRow> targets = recipients.recipients(idSet).stream().filter(r -> r.eventId() == eventId)
                    .filter(r -> r.status() == RewardRecipientStatus.GRANTED || r.status() == RewardRecipientStatus.PARTIAL).toList();
            if (targets.isEmpty()) {
                throw new RewardEventConflictException(RewardEventConflictException.REVOKE_NOTHING);
            }
            EventContext ctx = new EventContext(eventId, e.getName(), op.id());
            long points = 0;
            int items = 0;
            int failed = 0;
            for (RecipientRow r : targets) {
                Revoked rv = granter.revoke(ctx, r, text, op.id(), now);
                points += rv.points();
                items += rv.items();
                failed += rv.failed() ? 1 : 0;
            }
            long id = ids.nextId();
            events.insertRun(RewardEventRunEntity.done(id, eventId, RewardRunKind.REVOKE, op.id(), targets.size(), targets.size() - failed, failed, 0, points, items, text, now));
            support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_REVOKE, eventId, Map.of("runId", Long.toString(id), "count", targets.size(), "points", points,
                    "items", items, "failed", failed, "reason", text));
            support.settleDone(eventId, now);
            return id;
        });
        return runView(runId);
    }

    /** 직접 전달 완료 — 전달 대기 줄만({@code 409 REWARD_DELIVERY_STATE}), 메모 0~200자(개인정보 금지) */
    public RewardRecipientView markDelivered(RewardOperator op, long eventId, long recipientId, String lineId, String memo, UnaryOperator<String> emailView) {
        String text = memo == null ? "" : memo.strip();
        if (RewardEventRules.length(text) > RewardEventRules.DELIVERED_MEMO_MAX) {
            throw ValidationException.of("memo", new MessageRef("rewardEvent.delivered.memo.max", RewardEventRules.DELIVERED_MEMO_MAX));
        }
        long line = RewardEventRules.parseId(lineId).orElseThrow(() -> ValidationException.of("lineId", new MessageRef("rewardEvent.lineId.required")));
        return tx.execute(status -> {
            Instant now = now();
            support.lockFresh(eventId, now);
            RecipientRow r = recipients.recipient(recipientId).filter(x -> x.eventId() == eventId).orElseThrow(RewardRecipientNotFoundException::new);
            ResultRow result = recipients.results(recipientId).stream().filter(x -> x.lineId() == line).findFirst()
                    .orElseThrow(() -> new RewardEventConflictException(RewardEventConflictException.DELIVERY_STATE));
            if (!recipients.deliver(result.id(), op.id(), text, now)) {
                throw new RewardEventConflictException(RewardEventConflictException.DELIVERY_STATE);
            }
            if (r.status() != RewardRecipientStatus.REVOKED) {
                recipients.settle(recipientId, RewardLineGranter.settle(recipients.results(recipientId).stream().map(ResultRow::status).toList()));
            }
            support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_DELIVERED, eventId, Map.of("recipientId", Long.toString(recipientId), "userId", Long.toString(r.userId()),
                    "lineId", Long.toString(line), "memo", text));
            support.settleDone(eventId, now);
            return views.recipients(eventId, recipients.recipients(List.of(recipientId)), emailView).get(0);
        });
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 실행이 끝났나(시험·워커용) */
    public boolean finished(long runId) {
        return Boolean.TRUE.equals(tx.execute(status -> events.run(runId).map(r -> r.getStatus() == RewardRunStatus.DONE).orElse(true)));
    }
}
