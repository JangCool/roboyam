package com.community.point.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchParamDefinition;
import com.community.batch.core.BatchParamType;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.point.dto.PointRules;
import com.community.point.repository.master.PointPopularRewardStore;
import com.community.point.repository.master.PointPopularRewardStore.Pick;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 인기글 보상 Java 배치 {@code point-popular-reward}(point-board-tier-contract 「인기글 보상」) — 관리자 서버가 매일 0시(한국 시간)에 돈다.
 *
 * <ul>
 *   <li>대상 날짜: 파라미터 {@code day}(오늘보다 앞선 날, 없으면 어제 — 한국 시간). 정책 {@code popularRewardPoints}·{@code popularRewardTopN} 둘 중 0 이면 꺼짐(아무것도 하지 않는다).</li>
 *   <li><b>뽑기</b>: 그 날짜 목록이 아직 없으면 한 트랜잭션에서 상위 N 개를 뽑아 {@code point_popular_rewards} 에 남긴다({@link PointPopularRewardStore#pick} — 인기글 「어제」 탭과 같은
 *       기준). 이미 뽑았으면 다시 뽑지 않는다 — 점수가 바뀌어도 다른 글이 새로 받지 않는다.</li>
 *   <li><b>지급</b>: 남긴 목록을 순위 차례 덩어리로 {@link PointService#popularReward}(원장 {@code POPULAR}, 대상 글 — 원장 유일). 비회원 글·AI·운영팀 계정(스위치)·정상 회원 아님·동결은
 *       건너뛴다. 두 번 돌아도, 서버 둘이 돌아도 글마다 한 번이다.</li>
 * </ul>
 */
@Component
public class PointPopularRewardJob implements BatchJob {

    /** 실패 건의 대상 종류 — 보상 대상 글(순위) */
    public static final String TARGET = "POPULAR_REWARD";
    public static final String PARAM_DAY = "day";

    private final PointPopularRewardStore store;
    private final PointService points;
    private final PointPolicy policy;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PointPopularRewardJob(PointPopularRewardStore store, PointService points, PointPolicy policy, ObjectProvider<Clock> clocks,
                                 PlatformTransactionManager transactions) {
        this.store = store;
        this.points = points;
        this.policy = policy;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.POINT_POPULAR_REWARD;
    }

    @Override
    public List<BatchParamDefinition> params() {
        return List.of(new BatchParamDefinition(PARAM_DAY, BatchParamType.DATE, false));
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public Map<String, MessageRef> validate(BatchParameters parameters) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ServiceTime.ZONE);
        return parameters.date(PARAM_DAY).filter(day -> !day.isBefore(today)).<Map<String, MessageRef>>map(day -> Map.of(PARAM_DAY,
                new MessageRef("point.popularReward.day.notPast"))).orElse(Map.of());
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate day = context.parameters().date(PARAM_DAY).orElse(LocalDate.ofInstant(now, ServiceTime.ZONE).minusDays(1));
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        PointRules rules = policy.current();
        if (rules.popularRewardPoints() <= 0 || rules.popularRewardTopN() <= 0) {
            step.record(0, 0, 0);
            return BatchResult.of(new MessageRef("batch.result.pointPopularReward.off"));
        }
        Instant from = day.atStartOfDay(ServiceTime.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        int picked = transaction.execute(status -> store.picked(day) ? 0 : store.pick(day, from, to, now, rules.popularRewardTopN()));
        step.chunks(source(day), processor(rules.popularRewardPoints()));
        long rewarded = step.processedCount();
        long listed = step.readCount();
        if (rewarded == 0) {
            return BatchResult.of(new MessageRef("batch.result.pointPopularReward.none", day.toString()));
        }
        return BatchResult.of(new MessageRef("batch.result.pointPopularReward", day.toString(), String.valueOf(Math.max(listed, picked)), String.valueOf(rewarded)));
    }

    ChunkSource<Pick> source(LocalDate day) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<Pick> next(String afterKey, int size) {
                return store.picks(day, afterKey == null ? 0 : Integer.parseInt(afterKey), size);
            }

            @Override
            public List<Pick> byIds(List<String> targetIds) {
                return store.picks(day, targetIds.stream().map(id -> Integer.valueOf(id.substring(id.indexOf(':') + 1))).toList());
            }

            @Override
            public String keyOf(Pick item) {
                return Integer.toString(item.rank());
            }

            @Override
            public String targetIdOf(Pick item) {
                return day + ":" + item.rank();
            }
        };
    }

    /** 글쓴이마다 보상 — 실제로 준 건수(비회원 글·받을 수 없는 계정·이미 받은 글은 건너뜀) */
    ChunkProcessor<Pick> processor(int amount) {
        return items -> {
            long given = 0;
            for (Pick pick : items) {
                if (pick.authorId() != null && points.popularReward(pick.authorId(), pick.postId(), amount) > 0) {
                    given++;
                }
            }
            return given;
        };
    }
}
