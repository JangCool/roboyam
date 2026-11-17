package com.community.title.batch;

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
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.point.repository.master.PointPopularRewardStore;
import com.community.point.service.PointPolicy;
import com.community.title.dto.TitleCondition;
import com.community.title.entity.TitleConditionType;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleMethod;
import com.community.title.entity.TitleMetric;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleQueries.Counted;
import com.community.title.repository.master.TitleRepository;
import com.community.title.service.BuiltinTitles;
import com.community.title.service.TitleConditions;
import com.community.title.service.TitleEligibility;
import com.community.title.service.TitleGrantService;
import com.community.title.service.TitleSettingsReader;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 칭호 자동 판정 Java 배치 {@code title-auto-grant}(member-title-contract 「구현 결정」 1) — 관리자 서버가 매일 00:10(한국 시간)에 돈다(인기글 보상 00:00 뒤).
 *
 * <ul>
 *   <li><b>인기글 뽑기</b>: 대상 날짜(파라미터 {@code day}, 없으면 어제)의 인기글 상위 목록이 아직 없으면 인기글 보상과 <b>같은 뽑기</b>({@link PointPopularRewardStore#pick})로 남긴다 —
 *       포인트 보상이 꺼져 있어도(포인트와 상관없이) 상위 {@code popularRewardTopN}(0 이면 10) 개. 이미 뽑았으면 다시 뽑지 않는다.</li>
 *   <li><b>판정</b>: 받는 방법이 자동이고 켜진 칭호마다(파라미터 {@code title} 이 있으면 그 칭호만 — 칭호 저장 때의 소급) 세는 수의 문턱(단계 칭호는 동 문턱)을 넘은 회원을 회원 ID 차례 덩어리로
 *       고르고 {@link TitleGrantService#autoGrant} 로 나머지 조건을 확인해 주거나 단계를 올린다. AI 회원·운영팀 계정·탈퇴 회원은 후보에서 빠진다. 두 번 돌아도 같은 결과다(유일 색인·이미 가진 단계).</li>
 *   <li>명예 작가 후보 = 이달의 작가 살아 있는 줄이 설정 횟수 이상인 회원(평소에는 수여 트랜잭션이 바로 준다 — 배치는 놓친 것을 잇는다).</li>
 * </ul>
 */
@Component
public class TitleAutoGrantJob implements BatchJob {

    /** 실패 건의 대상 종류 — 칭호 키:회원 ID */
    public static final String TARGET = "TITLE_AUTO";
    public static final String PARAM_DAY = "day";
    public static final String PARAM_TITLE = "title";
    /** 포인트 정책의 상위 개수가 0 일 때 인기글 칭호가 쓰는 상위 개수(계약 「구현 결정」 1) */
    static final int DEFAULT_POPULAR_TOP_N = 10;

    private final TitleRepository titles;
    private final TitleQueries queries;
    private final TitleGrantService grants;
    private final TitleEligibility eligibility;
    private final TitleSettingsReader settings;
    private final PointPopularRewardStore popular;
    private final PointPolicy policy;
    private final BoardCatalog catalog;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TitleAutoGrantJob(TitleRepository titles, TitleQueries queries, TitleGrantService grants, TitleEligibility eligibility, TitleSettingsReader settings,
                             PointPopularRewardStore popular, PointPolicy policy, BoardCatalog catalog, ObjectProvider<Clock> clocks, PlatformTransactionManager transactions) {
        this.titles = titles;
        this.queries = queries;
        this.grants = grants;
        this.eligibility = eligibility;
        this.settings = settings;
        this.popular = popular;
        this.policy = policy;
        this.catalog = catalog;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.TITLE_AUTO_GRANT;
    }

    @Override
    public List<BatchParamDefinition> params() {
        return List.of(new BatchParamDefinition(PARAM_DAY, BatchParamType.DATE, false), new BatchParamDefinition(PARAM_TITLE, BatchParamType.STRING, false));
    }

    @Override
    public Map<String, MessageRef> validate(BatchParameters parameters) {
        Map<String, MessageRef> out = new HashMap<>();
        LocalDate today = LocalDate.ofInstant(clock.instant(), ServiceTime.ZONE);
        parameters.date(PARAM_DAY).filter(day -> !day.isBefore(today)).ifPresent(day -> out.put(PARAM_DAY, new MessageRef("point.popularReward.day.notPast")));
        parameters.string(PARAM_TITLE).filter(key -> titles.findByTitleKey(key).filter(t -> t.getMethod() == TitleMethod.AUTO).isEmpty())
                .ifPresent(key -> out.put(PARAM_TITLE, new MessageRef("title.batch.notAuto")));
        return out;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate day = context.parameters().date(PARAM_DAY).orElse(LocalDate.ofInstant(now, ServiceTime.ZONE).minusDays(1));
        Optional<String> only = context.parameters().string(PARAM_TITLE);
        int topN = policy.current().popularRewardTopN() > 0 ? policy.current().popularRewardTopN() : DEFAULT_POPULAR_TOP_N;
        Instant from = day.atStartOfDay(ServiceTime.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        transaction.execute(status -> popular.picked(day) ? 0 : popular.pick(day, from, to, now, topN));

        List<TitleEntity> targets = titles.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(t -> t.isEnabled() && t.getMethod() == TitleMethod.AUTO && only.map(key -> key.equals(t.getTitleKey())).orElse(true)).toList();
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        if (targets.isEmpty()) {
            step.record(0, 0, 0);
            return BatchResult.of(new MessageRef("batch.result.titleAutoGrant.none"));
        }
        step.chunks(source(targets), processor(targets));
        return BatchResult.of(new MessageRef("batch.result.titleAutoGrant", String.valueOf(targets.size()), String.valueOf(step.readCount()),
                String.valueOf(step.processedCount())));
    }

    /** 판정 대상 한 건 — 칭호 차례({@code index})·회원·센 수 */
    public record Item(int index, long titleId, long userId, long count) {
    }

    ChunkSource<Item> source(List<TitleEntity> targets) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<Item> next(String afterKey, int size) {
                int index = 0;
                long afterUser = 0;
                if (afterKey != null) {
                    int colon = afterKey.indexOf(':');
                    index = Integer.parseInt(afterKey.substring(0, colon));
                    afterUser = Long.parseLong(afterKey.substring(colon + 1));
                }
                List<Item> out = new ArrayList<>();
                BoardCatalogSnapshot s = catalog.snapshot();
                while (index < targets.size() && out.size() < size) {
                    TitleEntity title = targets.get(index);
                    for (Counted c : candidates(title, afterUser, size - out.size(), s)) {
                        out.add(new Item(index, title.getId(), c.userId(), c.count()));
                    }
                    if (out.size() < size) {
                        index++;
                        afterUser = 0;
                    }
                }
                return out;
            }

            @Override
            public List<Item> byIds(List<String> targetIds) {
                List<Item> out = new ArrayList<>();
                for (String id : targetIds) {
                    int colon = id.lastIndexOf(':');
                    String key = id.substring(0, colon);
                    long userId = Long.parseLong(id.substring(colon + 1));
                    for (int i = 0; i < targets.size(); i++) {
                        TitleEntity title = targets.get(i);
                        if (title.getTitleKey().equals(key)) {
                            out.add(new Item(i, title.getId(), userId, eligibility.metric(title, userId)));
                        }
                    }
                }
                return out;
            }

            @Override
            public String keyOf(Item item) {
                return item.index() + ":" + item.userId();
            }

            @Override
            public String targetIdOf(Item item) {
                return targets.get(item.index()).getTitleKey() + ":" + item.userId();
            }
        };
    }

    ChunkProcessor<Item> processor(List<TitleEntity> targets) {
        return items -> {
            long changed = 0;
            for (Item item : items) {
                if (grants.autoGrant(targets.get(item.index()), item.userId(), item.count())) {
                    changed++;
                }
            }
            return changed;
        };
    }

    /** 칭호 하나의 후보 — 세는 수의 문턱(단계 칭호는 동)을 넘은 회원, 회원 ID 차례 */
    List<Counted> candidates(TitleEntity title, long afterUser, int limit, BoardCatalogSnapshot s) {
        if (BuiltinTitles.HONORARY_WRITER.equals(title.getTitleKey())) {
            return titles.findByTitleKey(BuiltinTitles.MONTHLY_WRITER)
                    .map(m -> queries.titleCountCandidates(m.getId(), afterUser, settings.current().honoraryCount(), limit)).orElse(List.of());
        }
        TitleMetric metric = TitleEligibility.driver(title);
        Integer target = title.isTiered() ? title.getTierBronze() : TitleEligibility.driverTarget(title);
        long min = target == null ? 1 : target;
        if (metric == null) {
            return gradeOnly(title, afterUser, limit, s);
        }
        return switch (metric) {
            case POPULAR_DAYS -> queries.popularDayCandidates(afterUser, min, limit);
            case ATTENDANCE_DAYS -> queries.attendanceCandidates(afterUser, min, limit);
            case POSTS -> {
                TitleCondition posts = TitleEligibility.firstPosts(title).orElse(null);
                Optional<Long> board = posts == null ? Optional.empty() : TitleEligibility.board(posts, s);
                yield board == null ? List.of() : queries.postCandidates(board.orElse(null), afterUser, min, limit);
            }
            case TRIAL_KEPT -> queries.trialKeptCandidates(afterUser, min, limit); // 체험단 약속 지킴(V241)
        };
    }

    /** 세는 조건이 없는 칭호 — 등급 조건이 있으면 그 등급 이상 회원, 없으면 후보 없음(조건 없는 자동 칭호는 아무에게도 주지 않는다) */
    private List<Counted> gradeOnly(TitleEntity title, long afterUser, int limit, BoardCatalogSnapshot s) {
        TitleCondition grade = TitleConditions.read(title.getConditions()).stream().filter(c -> c.type() == TitleConditionType.GRADE_AT_LEAST).findFirst().orElse(null);
        Integer need = grade == null ? null : s.gradeLevel(grade.gradeKey());
        if (need == null) {
            return List.of();
        }
        List<String> keys = s.grades().values().stream().filter(g -> g.level() >= need).map(BoardCatalogSnapshot.GradeInfo::key).toList();
        return queries.gradeCandidates(keys, afterUser, limit);
    }
}
