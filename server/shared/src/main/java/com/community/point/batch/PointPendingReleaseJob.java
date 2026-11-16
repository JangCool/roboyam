package com.community.point.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.point.entity.PointPendingGrantEntity;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointPendingGrantStore;
import com.community.point.repository.master.PointPendingGrantStore.Due;
import com.community.point.service.PointService;
import com.community.point.service.PointService.HeldRelease;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 포인트 보류 확정 Java 배치 {@code point-pending-release}(point-board-tier-contract 「보류 확정 방식」) — 관리자 서버(배치를 켠 인스턴스)가 5분마다 돈다.
 *
 * <ul>
 *   <li>대상: 확정 예정 시각({@code release_at})이 실행 시작 시각 이하인 대기 중(PENDING) 보류 <b>전부</b> — (예정 시각, ID) 키셋 덩어리. 읽기는 잠그지 않는다.</li>
 *   <li>건마다 보류 행을 {@code SKIP LOCKED} 로 잠근다({@link PointPendingGrantStore#lockDue}) — 다른 서버의 배치·같은 대상의 취소가 쥐고 있으면 건너뛴다. 잡았으면
 *       {@link PointService#releaseHeld}(대상 살아 있음·선정 살아 있음·정상 회원·동결 아님 → 지급 한 길)로 확정하거나 그 까닭으로 취소한다. 잠금 차례는 보류 행 → 잔액 행(삭제의 취소와 같다).</li>
 *   <li><b>멱등·한 번만</b>: 상태가 PENDING 일 때만 잡고 같은 트랜잭션에서 RELEASED·CANCELLED 로 바꾼다. 원장 유일 키 {@code (reason, ref_type, ref_id)} 가 한 번 더 막는다
 *       — 두 서버가 같이 돌아도, 처음부터·체크포인트 다음부터 다시 돌려도 한 번만 지급된다.</li>
 * </ul>
 */
@Component
public class PointPendingReleaseJob implements BatchJob {

    /** 실패 건의 대상 종류 — 보류 지급 */
    public static final String TARGET = "POINT_PENDING";

    private final PointPendingGrantStore store;
    private final PointService points;
    private final Clock clock;
    private final TransactionTemplate readOnly;

    public PointPendingReleaseJob(PointPendingGrantStore store, PointService points, ObjectProvider<Clock> clocks, PlatformTransactionManager transactions) {
        this.store = store;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.points = points;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.POINT_PENDING_RELEASE;
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // 실행 시작 시각 — 실행 중에 예정 시각이 되는 보류는 다음 실행이 맡는다
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.chunks(source(now), processor(now));
        if (step.processedCount() == 0) {
            return BatchResult.of(new MessageRef("batch.result.pointPendingRelease.none"));
        }
        long[] settled = readOnly.execute(status -> store.settledAt(now));
        return BatchResult.of(new MessageRef("batch.result.pointPendingRelease", String.valueOf(settled[0]), String.valueOf(settled[1])));
    }

    ChunkSource<Due> source(Instant now) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<Due> next(String afterKey, int size) {
                return store.due(now, parseKey(afterKey), size);
            }

            @Override
            public List<Due> byIds(List<String> targetIds) {
                return store.dueByIds(now, targetIds.stream().map(Long::valueOf).toList());
            }

            @Override
            public String keyOf(Due item) {
                return key(item);
            }

            @Override
            public String targetIdOf(Due item) {
                return Long.toString(item.id());
            }
        };
    }

    /** 건마다 잠그고 확정·취소 — 끝낸 건수(다른 서버가 쥐었거나 이미 끝난 건은 건너뜀) */
    ChunkProcessor<Due> processor(Instant now) {
        return items -> {
            long done = 0;
            for (Due due : items) {
                Optional<PointPendingGrantEntity> locked = store.lockDue(due.id(), now);
                if (locked.isEmpty()) {
                    continue;
                }
                PointPendingGrantEntity row = locked.get();
                HeldRelease outcome = points.releaseHeld(row.getUserId(), PointReason.valueOf(row.getReason()), row.getRefType(), row.getRefId(), row.getAmount());
                if (outcome == HeldRelease.RELEASED) {
                    row.release(now);
                } else {
                    row.cancel(outcome.name(), now);
                }
                done++;
            }
            return done;
        };
    }

    /** 체크포인트 — 「예정 시각(에포크 마이크로초):보류 ID」 */
    static String key(Due due) {
        Instant at = due.releaseAt();
        long micros = Math.addExact(Math.multiplyExact(at.getEpochSecond(), 1_000_000L), at.getNano() / 1_000L);
        return micros + ":" + due.id();
    }

    static Due parseKey(String key) {
        if (key == null) {
            return null;
        }
        int colon = key.indexOf(':');
        long micros = Long.parseLong(key.substring(0, colon));
        long id = Long.parseLong(key.substring(colon + 1));
        return new Due(id, Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L));
    }
}
