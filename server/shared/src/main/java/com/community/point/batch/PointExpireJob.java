package com.community.point.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchParamDefinition;
import com.community.batch.core.BatchParamType;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.point.repository.master.PointLotBatchQueries;
import com.community.point.repository.master.PointLotBatchQueries.DueLot;
import com.community.point.service.PointService;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 포인트 소멸 Java 배치 {@code point-expire}(docs/domain/commerce.md 15-3·15-5 「포인트 소멸 — Java 배치, 매일 00:10 KST」, docs/features/commerce-stage1-contract.md 「A」·「1-1 서버 세부」).
 *
 * <ul>
 *   <li>대상: 무상({@code FREE})이고 남은 양이 있고 소멸일이 지난({@code expires_at ≤ 실행 시작 시각}) 묶음 <b>전부</b> — 「그날 몫」이 아니라 「아직 처리 안 한 것 전부」라 하루를 놓쳐도
 *       다음 실행이 메꾼다. 유상 묶음은 소멸하지 않는다(D12).</li>
 *   <li>(소멸일, ID) 키셋으로 덩어리(작업별 {@code chunk_size}, 기본 500)를 읽고, 덩어리마다 <b>묶음마다</b> {@link PointService#expireLot} 를 부른다 — 소멸 규칙(잔액 행 잠금 → 다시 읽기 →
 *       조건부 {@code EXPIRE} 줄)은 서비스 한 곳에만 있다(돈 규칙을 두 곳에 두지 않는다 — 15-5). 덩어리 하나가 한 트랜잭션이고 같은 트랜잭션에서 단계 건수·체크포인트가 고쳐진다.</li>
 *   <li><b>멱등</b>: 서비스가 이미 소멸했거나(남은 양 0 — 사용·회수 전에 서비스가 지난 묶음을 먼저 소멸한다) 아직 소멸일 전이면 0 이라 「건너뜀」으로 센다. 그래서 처음부터 다시 돌려도,
 *       체크포인트 다음부터 다시 돌려도(「실패 지점부터 다시」) 안전하다.</li>
 *   <li>덩어리가 실패하면 그 덩어리만 묶음 하나씩 다시 해 실패한 묶음만 남긴다(대상 {@code POINT_LOT}, 묶음 ID). 「실패 건만 다시」는 그 묶음들만 다시 읽는다.</li>
 *   <li>파라미터 {@code userId}(선택) — 그 회원의 묶음만.</li>
 * </ul>
 * 동결(POINT_FREEZE) 회원도 소멸한다(계약 「1-1 서버 세부」 — 동결은 적립·사용만 막는다).
 */
@Component
public class PointExpireJob implements BatchJob {

    /** 실패 건의 대상 종류 — 묶음 */
    public static final String TARGET = "POINT_LOT";
    public static final String PARAM_USER = "userId";

    private final PointLotBatchQueries queries;
    private final PointService points;
    private final Clock clock;

    public PointExpireJob(PointLotBatchQueries queries, PointService points, ObjectProvider<Clock> clocks) {
        this.queries = queries;
        this.points = points;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.POINT_EXPIRE;
    }

    @Override
    public List<BatchParamDefinition> params() {
        return List.of(new BatchParamDefinition(PARAM_USER, BatchParamType.LONG, false));
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // 실행 시작 시각 — 실행 중에 소멸일이 되는 묶음은 다음 실행(또는 서비스의 사용 전 소멸)이 맡는다
        Long userId = context.parameters().longValue(PARAM_USER).orElse(null);
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.chunks(source(now, userId), processor());
        long expired = step.processedCount();
        return BatchResult.of(expired == 0 ? new MessageRef("batch.result.pointExpire.none") : new MessageRef("batch.result.pointExpire", String.valueOf(expired)));
    }

    ChunkSource<DueLot> source(Instant now, Long userId) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<DueLot> next(String afterKey, int size) {
                return queries.dueLots(now, parseKey(afterKey), userId, size);
            }

            @Override
            public List<DueLot> byIds(List<String> targetIds) {
                return queries.dueLotsByIds(now, targetIds.stream().map(Long::valueOf).toList());
            }

            @Override
            public String keyOf(DueLot item) {
                return key(item);
            }

            @Override
            public String targetIdOf(DueLot item) {
                return Long.toString(item.id());
            }
        };
    }

    /** 묶음마다 서비스의 조건부 소멸 — 소멸한 묶음 수(나머지는 건너뜀) */
    ChunkProcessor<DueLot> processor() {
        return items -> {
            long expired = 0;
            for (DueLot lot : items) {
                if (points.expireLot(lot.id()) > 0) {
                    expired++;
                }
            }
            return expired;
        };
    }

    /** 체크포인트 — 「소멸일(에포크 마이크로초):묶음 ID」 */
    static String key(DueLot lot) {
        Instant at = lot.expiresAt();
        long micros = Math.addExact(Math.multiplyExact(at.getEpochSecond(), 1_000_000L), at.getNano() / 1_000L);
        return micros + ":" + lot.id();
    }

    static DueLot parseKey(String key) {
        if (key == null) {
            return null;
        }
        int colon = key.indexOf(':');
        long micros = Long.parseLong(key.substring(0, colon));
        long id = Long.parseLong(key.substring(colon + 1));
        Instant at = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
        return new DueLot(id, 0, at);
    }
}
