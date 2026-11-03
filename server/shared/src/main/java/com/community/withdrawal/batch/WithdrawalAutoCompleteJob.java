package com.community.withdrawal.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.dto.WithdrawalPending;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.withdrawal.repository.master.MemberWithdrawalStore;
import com.community.withdrawal.repository.master.WithdrawalQueries;
import com.community.withdrawal.service.WithdrawalCompletionService;
import com.community.withdrawal.service.WithdrawalMailSender;
import com.community.withdrawal.service.WithdrawalPendingResolver;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 탈퇴 예약 자동 완료 Java 배치 {@code member-withdrawal-complete}(commerce.md 17-1 D1, member-withdrawal-contract) — 진행 중(SCHEDULED) 신청마다 남은 일을
 * {@link WithdrawalPendingResolver} 로 다시 세어 <b>없으면 {@link WithdrawalCompletionService#complete} 로 익명화 완료</b>(신청 즉시 완료와 같은 코드), 남아 있으면 스냅샷을 고치고,
 * 신청 뒤 {@code progress-notice-days}(기본 10일)가 지났는데 아직 진행 안내를 안 보냈으면 진행 상태 메일을 한 번 보낸다.
 *
 * <p>덩어리 처리 도우미({@link BatchStep#chunks} — 대상 {@code MEMBER_WITHDRAWAL} = 신청 ID 키셋)를 쓴다: 덩어리가 실패하면 한 건씩 다시 처리해 <b>한 회원의 실패가 나머지를 막지 않고</b>
 * 실패 건은 실행 기록({@code batch_run_failures})에 남는다. 단계 건수: 읽음 = 살펴본 신청, 처리 = 완료한 신청, 건너뜀 = 아직 남은 일이 있는 신청(진행 안내 포함), 실패 = 예외가 난 신청.
 * 멱등 — 완료한 신청은 SCHEDULED 가 아니라 다시 집히지 않고, 진행 안내는 {@code progress_notified_at} 으로 한 번만 보낸다.
 */
@Component
public class WithdrawalAutoCompleteJob implements BatchJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalAutoCompleteJob.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 실패 건의 대상 종류(번들 {@code batch.target.MEMBER_WITHDRAWAL}) */
    public static final String TARGET = "MEMBER_WITHDRAWAL";

    private final MemberWithdrawalStore withdrawals;
    private final WithdrawalQueries queries;
    private final WithdrawalPendingResolver pending;
    private final WithdrawalCompletionService completion;
    private final WithdrawalMailSender mail;
    private final WithdrawalProperties properties;
    private final Clock clock;

    public WithdrawalAutoCompleteJob(MemberWithdrawalStore withdrawals, WithdrawalQueries queries, WithdrawalPendingResolver pending, WithdrawalCompletionService completion,
                                     WithdrawalMailSender mail, WithdrawalProperties properties, ObjectProvider<Clock> clocks) {
        this.withdrawals = withdrawals;
        this.queries = queries;
        this.pending = pending;
        this.completion = completion;
        this.mail = mail;
        this.properties = properties;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.MEMBER_WITHDRAWAL_COMPLETE;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        AtomicLong notified = new AtomicLong();
        step.chunks(source(), processor(notified));
        long completed = step.processedCount();
        long waiting = step.skippedCount() - Math.min(step.skippedCount(), notified.get());
        return BatchResult.of(completed == 0 && notified.get() == 0 ? new MessageRef("batch.result.memberWithdrawalComplete.none", String.valueOf(step.readCount()))
                : new MessageRef("batch.result.memberWithdrawalComplete", String.valueOf(completed), String.valueOf(notified.get()), String.valueOf(waiting)));
    }

    /** 진행 중 신청 ID — 신청 ID 키셋 */
    ChunkSource<Long> source() {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<Long> next(String afterKey, int size) {
                return withdrawals.scheduledIds(afterKey == null ? 0 : Long.parseLong(afterKey), size);
            }

            @Override
            public List<Long> byIds(List<String> targetIds) {
                return targetIds.stream().map(Long::valueOf).filter(id -> withdrawals.find(id).filter(r -> r.status() == WithdrawalStatus.SCHEDULED).isPresent()).toList();
            }

            @Override
            public String keyOf(Long item) {
                return Long.toString(item);
            }

            @Override
            public String targetIdOf(Long item) {
                return Long.toString(item);
            }
        };
    }

    /** 신청마다 — 완료한 수를 돌려준다(나머지는 건너뜀으로 센다). 예외는 그대로 던져 실행기가 한 건씩 다시 처리하고 실패를 기록하게 한다 */
    ChunkProcessor<Long> processor(AtomicLong notified) {
        return items -> {
            long completed = 0;
            for (Long id : items) {
                Outcome outcome = process(id);
                if (outcome == Outcome.COMPLETED) {
                    completed++;
                } else if (outcome == Outcome.NOTIFIED) {
                    notified.incrementAndGet();
                }
            }
            return completed;
        };
    }

    /** 신청 하나의 처리 결과 — 완료 · 진행 안내 보냄 · 아직 남은 일 있음 · 대상 아님(이미 완료·취소·상태 불일치) */
    public enum Outcome {
        COMPLETED, NOTIFIED, WAITING, GONE
    }

    /** 신청 하나 — 남은 일을 다시 세어 없으면 완료, 있으면 스냅샷·진행 안내. 배치 덩어리와 시험이 부른다(부르는 쪽 트랜잭션 안에서) */
    public Outcome process(long withdrawalId) {
        MemberWithdrawalStore.Row row = withdrawals.find(withdrawalId).filter(r -> r.status() == WithdrawalStatus.SCHEDULED).orElse(null);
        if (row == null) {
            return Outcome.GONE;
        }
        Instant now = clock.instant();
        Optional<WithdrawalQueries.Member> member = queries.member(row.userId());
        if (member.isEmpty() || !"WITHDRAWING".equals(member.get().status())) {
            log.warn("탈퇴 진행 중 신청인데 회원 상태가 다르다 withdrawalId={} userId={}", withdrawalId, row.userId());
            return Outcome.GONE;
        }
        long paidPoints = queries.losses(row.userId(), ServiceTime.dateOf(now), now).paidPoints();
        List<WithdrawalPending> remaining = pending.all(row.userId(), row.choices(), paidPoints, now);
        if (remaining.isEmpty()) {
            return completion.complete(row.userId(), false, null).isPresent() ? Outcome.COMPLETED : Outcome.GONE;
        }
        Instant expected = WithdrawalPendingResolver.expectedCompleteAt(remaining).orElse(null);
        withdrawals.updatePending(withdrawalId, JSON.writeValueAsString(remaining), remaining.size(), expected);
        boolean due = !row.requestedAt().plus(Duration.ofDays(properties.progressNoticeDays())).isAfter(now);
        if (due && row.progressNotifiedAt() == null) {
            withdrawals.markProgressNotified(withdrawalId, now);
            if (member.get().emailVerified()) {
                mail.progressAfterCommit(row.userId(), member.get().email(), member.get().language(), member.get().nickname(), remaining, expected);
            }
            return Outcome.NOTIFIED;
        }
        return Outcome.WAITING;
    }
}
