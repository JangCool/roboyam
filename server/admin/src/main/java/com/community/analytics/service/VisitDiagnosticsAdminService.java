package com.community.analytics.service;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.dto.VisitAnalyticsAdmin.HealthCron;
import com.community.analytics.dto.VisitAnalyticsAdmin.HealthInstance;
import com.community.analytics.dto.VisitAnalyticsAdmin.HealthIssue;
import com.community.analytics.repository.master.VisitHealthStore;
import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchMessageCodec;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.repository.master.BatchRunRepositoryQDSL;
import com.community.batch.service.BatchCronPolicy;
import com.community.batch.service.DbBatchGateway;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 「방문자 통계 › 수집 진단」·「정보주체 요청」(site-analytics-contract 「2단계」) — 관리자 API 2개의 계산. 모두 site.read(컨트롤러), 응답 모양은 {@link VisitAnalyticsAdmin}.
 *
 * <p><b>수집 진단 판정</b>(기간 없음 — 최근 {@link #WINDOW}, 캐시 {@link #HEALTH_TTL}):
 * <ul>
 *   <li>서버(instance)는 신호가 없어도 1분마다 「살아 있음」을 쓴다. 마지막 기록이 {@link #SILENT_WARN} 보다 오래면 {@code INSTANCE_SILENT} WARN, {@link #SILENT_ERROR} 보다 오래면 ERROR
 *       (최근 24시간 안에 기록한 서버만 — 그 전에 멈춘 서버는 목록에서 빠지고 7일 뒤 정리된다).</li>
 *   <li>{@code FLUSH_FAILED}: 서버별 24시간 기록 실패 수 — 지난 {@link #RECENT_FAIL} 안에도 실패가 있으면 ERROR, 아니면 WARN. {@code BUFFER_DROPPED}: 버린 줄이 있으면 WARN.</li>
 *   <li>크론(배치 로그 {@code batch_runs} 의 마지막 실행): 마지막이 FAIL 이면 {@code CRON_FAILED} ERROR. 마지막 실행 뒤 예정 시각을 두 번 지나도 돌지 않았으면(주기의 2배)
 *       {@code CRON_LATE} WARN — pg_cron 에 등록되어 켜진 배치만, 식은 pg_cron 의 지금 식.</li>
 *   <li>{@code RAW_BACKLOG}: 가장 오래된 원본의 날이 「이번 달부터 (보관 개월 수 − 1)달 앞의 1일」보다 앞이면(3단계 — 달 단위 보관. 그 달 1일에는 정리가 하루 늦는 것까지 정상이라
 *       한 달 더 앞까지 본다, {@link #rawKeepFrom}) 넘친 날 수로 WARN.
 *       {@code NEXT_PARTITION_MISSING}: 원본 기록이 켜졌는데 다음 달 조각이 없으면 WARN(그 달의 마지막 {@link #PARTITION_URGENT_DAYS}일이면 ERROR).</li>
 *   <li>{@code COLLECT_OFF}: 방문 쪽 수집(신호·검색어·봇)이 하나도 켜지지 않음 — 상태 OFF.</li>
 *   <li>상태: OFF(꺼짐) → STOPPED(최근 {@link #SILENT_WARN} 안에 기록한 서버가 하나도 없음) → DELAYED(문제가 하나라도) → OK. 문제는 ERROR 먼저, 같으면 코드 차례.</li>
 * </ul>
 *
 * <p><b>정보주체 요청</b>: 최신 차례 한 쪽(기본 20 · 최대 100, 틀리면 400 {@code fields.page}·{@code fields.size}) + 대기 수 + 지난 30일 수. 대기 줄의 지운 줄 수·처리 시각은 null.
 */
@Service
public class VisitDiagnosticsAdminService {

    static final Duration HEALTH_TTL = Duration.ofSeconds(20);
    static final Duration REQUESTS_TTL = Duration.ofSeconds(60);
    static final Duration WINDOW = Duration.ofHours(24);
    static final Duration SILENT_WARN = Duration.ofMinutes(3);
    static final Duration SILENT_ERROR = Duration.ofMinutes(10);
    static final Duration RECENT_FAIL = Duration.ofHours(1);
    static final int FAILURES = 5;
    static final int PARTITION_URGENT_DAYS = 3;
    static final int PAGE_DEFAULT_SIZE = 20;
    static final int PAGE_MAX_SIZE = 100;
    static final int PAGE_MAX_INDEX = 10_000;

    /** 화면에 보이는 크론 — 방문 분석 크론 + 같은 「설정 › 통계 수집」에 딸린 메인 DB 크론 */
    static final List<String> CRON_JOBS = List.of(BatchCatalog.ANALYTICS_PAGE_VIEW_PARTITIONS.key(), BatchCatalog.ANALYTICS_VISIT_MARK_CLEANUP.key(),
            BatchCatalog.ANALYTICS_VISIT_ACTIVE_CLEANUP.key(), BatchCatalog.ANALYTICS_FORGET_REQUESTS.key(), BatchCatalog.ANALYTICS_LOG_CLEANUP.key(),
            BatchCatalog.ANALYTICS_MONTHLY_ROLLUP.key(),
            BatchCatalog.BOARD_ACTIVITY_DAILY.key(), BatchCatalog.NOTIFICATION_SEND_FAILURE_CLEANUP.key());
    /** 문제 코드 차례(같은 심각도 안에서) — admin-web {@code VISIT_HEALTH_ISSUE_CODES} */
    static final List<String> ISSUE_CODES = List.of("INSTANCE_SILENT", "FLUSH_FAILED", "BUFFER_DROPPED", "CRON_FAILED", "CRON_LATE", "RAW_BACKLOG",
            "NEXT_PARTITION_MISSING", "COLLECT_OFF");

    private final VisitHealthStore store;
    private final StatsCollectionSettings settings;
    private final BatchRunRepositoryQDSL runs;
    private final DbBatchGateway database;
    private final BatchCronPolicy cronPolicy;
    private final Messages messages;
    private final Clock clock;
    private final VisitResultCache cache;

    public VisitDiagnosticsAdminService(VisitHealthStore store, StatsCollectionSettings settings, BatchRunRepositoryQDSL runs, DbBatchGateway database,
                                        BatchCronPolicy cronPolicy, Messages messages, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.settings = settings;
        this.runs = runs;
        this.database = database;
        this.cronPolicy = cronPolicy;
        this.messages = messages;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
        this.cache = new VisitResultCache(clock);
    }

    // ── 수집 진단 ────────────────────────────────────────────────────────────

    /** 운영자 모두 같은 키(언어 — 크론 사유 문구 — 와 수집 스위치만) */
    public VisitAnalyticsAdmin.Health health() {
        StatsCollection s = settings.current();
        return cache.get("health|" + messages.locale().getLanguage() + "|" + VisitAnalyticsAdminService.fingerprint(s), HEALTH_TTL, at -> computeHealth(s, at));
    }

    private VisitAnalyticsAdmin.Health computeHealth(StatsCollection s, Instant at) {
        List<VisitHealthStore.InstanceRow> rows = store.instances(at.minus(WINDOW), at.minus(RECENT_FAIL));
        List<VisitAnalyticsAdmin.HealthFailure> failures = store.failures(at.minus(WINDOW), FAILURES).stream()
                .map(f -> new VisitAnalyticsAdmin.HealthFailure(f.at(), f.instance(), f.reason(), f.detail())).toList();
        VisitHealthStore.RawRow raw = store.raw();
        List<HealthCron> crons = crons(at);

        List<HealthIssue> issues = new ArrayList<>();
        List<HealthInstance> instances = new ArrayList<>(rows.size());
        int live = 0;
        for (VisitHealthStore.InstanceRow r : rows) {
            instances.add(new HealthInstance(r.instance(), r.lastWriteAt(), r.flushOk(), r.flushFail(), r.dropped(), r.ignoredBot(), r.ignoredInvalid(),
                    r.ignoredExcluded(), r.unknownPath()));
            if (r.lastWriteAt() != null && r.lastWriteAt().isAfter(at.minus(SILENT_WARN))) {
                live++;
            } else if (r.lastWriteAt() != null) {
                String level = r.lastWriteAt().isAfter(at.minus(SILENT_ERROR)) ? "WARN" : "ERROR";
                issues.add(new HealthIssue("INSTANCE_SILENT", level, r.instance(), null, null, r.lastWriteAt()));
            }
            if (r.flushFail() > 0) {
                issues.add(new HealthIssue("FLUSH_FAILED", r.recentFail() > 0 ? "ERROR" : "WARN", r.instance(), null, r.flushFail(), null));
            }
            if (r.dropped() > 0) {
                issues.add(new HealthIssue("BUFFER_DROPPED", "WARN", r.instance(), null, r.dropped(), null));
            }
        }
        for (HealthCron c : crons) {
            if ("FAIL".equals(c.lastStatus())) {
                issues.add(new HealthIssue("CRON_FAILED", "ERROR", null, c.job(), null, c.lastRunAt()));
            } else if (c.late()) {
                issues.add(new HealthIssue("CRON_LATE", "WARN", null, c.job(), null, c.lastRunAt()));
            }
        }
        LocalDate today = ServiceTime.dateOf(at);
        LocalDate oldestDay = raw.oldest() == null ? null : ServiceTime.dateOf(raw.oldest());
        if (oldestDay != null) {
            long backlog = ChronoUnit.DAYS.between(oldestDay, rawKeepFrom(today, s.rawRetentionMonths()));
            if (backlog > 0) {
                issues.add(new HealthIssue("RAW_BACKLOG", "WARN", null, null, backlog, null));
            }
        }
        if (s.rawEventsOn() && !raw.nextPartitionReady()) {
            int daysLeft = today.lengthOfMonth() - today.getDayOfMonth() + 1;
            issues.add(new HealthIssue("NEXT_PARTITION_MISSING", daysLeft <= PARTITION_URGENT_DAYS ? "ERROR" : "WARN", null, null, null, null));
        }
        boolean off = !s.anyCollectOn();
        if (off) {
            issues.add(new HealthIssue("COLLECT_OFF", "WARN", null, null, null, null));
        }
        issues.sort(Comparator.comparingInt((HealthIssue i) -> "ERROR".equals(i.level()) ? 0 : 1).thenComparingInt(i -> ISSUE_CODES.indexOf(i.code())));
        String status = off ? "OFF" : live == 0 ? "STOPPED" : issues.isEmpty() ? "OK" : "DELAYED";
        VisitAnalyticsAdmin.HealthRaw rawView = new VisitAnalyticsAdmin.HealthRaw(s.rawEventsOn(), s.rawRetentionMonths(), oldestDay == null ? null : oldestDay.toString(),
                raw.partitions(), raw.approxRows(), raw.nextPartitionReady());
        return new VisitAnalyticsAdmin.Health(at.truncatedTo(ChronoUnit.SECONDS), status, issues, instances, failures, crons, rawView);
    }

    /**
     * 원본이 남아 있어도 되는 첫 날 — 「이번 달부터 (보관 개월 수 − 1)달 앞」의 1일(R__analytics_10 이 그 앞 달 조각을 지운다). 정리 크론(매일 00:25)이 하루 늦는 것까지는 정상이라
     * 그 달 1일에는 한 달 더 앞까지 본다(일 단위 판정의 「+1일」 여유와 같은 뜻).
     */
    static LocalDate rawKeepFrom(LocalDate today, int months) {
        LocalDate keepFrom = today.withDayOfMonth(1).minusMonths(months - 1L);
        return today.getDayOfMonth() == 1 ? keepFrom.minusMonths(1) : keepFrom;
    }

    /** 크론 줄 — 배치 로그의 마지막 실행 + pg_cron 의 지금 식(쓸 수 없으면 정의의 기본 식, 늦음 판정 없음) */
    private List<HealthCron> crons(Instant at) {
        Map<String, BatchRunEntity> latest = runs.latestByJobKey(CRON_JOBS);
        DbBatchGateway.PgCronState state = database.state();
        Map<String, DbBatchGateway.DbJob> jobs = database.jobs(state.ready());
        ZoneId zone = zone(state.timezone());
        List<HealthCron> out = new ArrayList<>(CRON_JOBS.size());
        for (String key : CRON_JOBS) {
            DbBatchGateway.DbJob job = jobs.get(key);
            boolean scheduled = state.ready() && job != null && job.scheduled();
            String cron = scheduled ? job.schedule() : job == null ? null : job.defaultCron();
            BatchRunEntity run = latest.get(key);
            Instant lastRunAt = run == null ? null : run.getStartedAt();
            String status = run == null || run.getStatus() == null ? null : run.getStatus().name();
            String message = null;
            if (run != null && run.getStatus() == BatchRunStatus.FAIL) {
                message = run.getErrorMessage();
            } else if (run != null && run.getStatus() == BatchRunStatus.SKIP) {
                MessageRef summary = BatchMessageCodec.decode(run.getMessageKey(), run.getMessageArgs());
                message = summary == null ? null : messages.get(summary);
            }
            boolean late = scheduled && job.active() && lastRunAt != null && late(cron, lastRunAt, at, zone);
            out.add(new HealthCron(key, cron, lastRunAt, status, run == null ? null : run.getAffectedCount(), message, late));
        }
        return out;
    }

    /** 마지막 실행 뒤 예정 시각을 두 번 지났나(주기의 2배) — 식을 읽을 수 없으면 false */
    private boolean late(String cron, Instant lastRunAt, Instant at, ZoneId zone) {
        if (cron == null) {
            return false;
        }
        try {
            ZonedDateTime first = cronPolicy.nextPgCron(cron, lastRunAt.atZone(zone));
            ZonedDateTime second = first == null ? null : cronPolicy.nextPgCron(cron, first);
            return second != null && at.isAfter(second.toInstant());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static ZoneId zone(String name) {
        try {
            return ZoneId.of(name);
        } catch (DateTimeException | NullPointerException e) {
            return ZoneOffset.UTC;
        }
    }

    // ── 정보주체 요청 ────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.ForgetPage requests(String pageText, String sizeText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef range = new MessageRef("analytics.visits.page.range", PAGE_MAX_SIZE);
        Integer page = integer(pageText, 0, 0, PAGE_MAX_INDEX);
        if (page == null) {
            fields.put("page", range);
        }
        Integer size = integer(sizeText, PAGE_DEFAULT_SIZE, 1, PAGE_MAX_SIZE);
        if (size == null) {
            fields.put("size", range);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return cache.get("requests\u0001" + page + "\u0001" + size, REQUESTS_TTL, at -> computeRequests(page, size, at));
    }

    private VisitAnalyticsAdmin.ForgetPage computeRequests(int page, int size, Instant at) {
        VisitHealthStore.ForgetCounts counts = store.forgetCounts(at.minus(Duration.ofDays(30)));
        List<VisitAnalyticsAdmin.ForgetRequest> items = (long) page * size < counts.total()
                ? store.forgetRequests(page * size, size).stream().map(VisitDiagnosticsAdminService::request).toList()
                : List.of();
        return new VisitAnalyticsAdmin.ForgetPage(items, counts.total(), counts.pending(), counts.recent());
    }

    private static VisitAnalyticsAdmin.ForgetRequest request(VisitHealthStore.ForgetRow r) {
        boolean pending = !"DONE".equals(r.status());
        return new VisitAnalyticsAdmin.ForgetRequest(Long.toString(r.id()), r.receivedAt(), pending ? "PENDING" : "DONE", pending ? null : r.deletedRows(),
                pending ? null : r.processedAt());
    }

    private static Integer integer(String text, int fallback, int min, int max) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(text.strip());
            return v < min || v > max ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
