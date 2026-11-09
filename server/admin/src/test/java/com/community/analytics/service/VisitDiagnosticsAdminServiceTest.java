package com.community.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.repository.master.VisitHealthStore;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.FakeMessages;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.MutableClock;
import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.repository.master.BatchRunRepositoryQDSL;
import com.community.batch.service.BatchCronPolicy;
import com.community.batch.repository.master.PgCronQueries;
import com.community.batch.service.DbBatchGateway;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.flyway.SchemaMigration;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 「방문자 통계 › 수집 진단」 판정과 「정보주체 요청」 목록(site-analytics-contract 「2단계 — 진단」·「정보주체 요청」) — 진단 표·요청 표는 실제 PostgreSQL(시험마다 되돌린다),
 * 원본 보관(카탈로그)·배치 로그·pg_cron 상태는 손으로 만든 가짜. 고정 시계 2098년 5월 20일 10:20:30 한국.
 *
 * <p>판정 수치: 마지막 기록 3분 WARN · 10분 ERROR, 기록 실패 24시간 WARN(지난 1시간이면 ERROR), 버린 신호 WARN, 크론 마지막 실패 ERROR · 예정 두 번 지남 WARN,
 * 원본 보관 + 1일 넘음 WARN, 다음 달 조각 없음 WARN(그 달 마지막 3일 ERROR). 상태 OFF → STOPPED → DELAYED → OK, 문제는 ERROR 먼저 · 같으면 코드 차례.
 */
class VisitDiagnosticsAdminServiceTest {

    static final Instant NOW = kst(5, 20, 10, 20, 30);
    static final StatsCollection ALL2 = new StatsCollection(true, true, true, 90, true, true, true, true, true, false, false);
    static final String FORGET_JOB = "analytics-forget-requests";

    private static DataSource base;
    private Connection connection;
    private JdbcTemplate jdbc;
    private FakeHealthStore store;
    private FakeRuns runs;
    private FakeGateway gateway;
    private volatile StatsCollection current = ALL2;
    private MutableClock clock;

    @BeforeAll
    static void tables() {
        base = CommerceTestDatabase.migrated("admin");
        new SchemaMigration("filesystem:../api/src/main/resources/db/migration/").migrateStream(base, SchemaMigration.ANALYTICS);
    }

    @BeforeEach
    void open() throws Exception {
        connection = base.getConnection();
        connection.setAutoCommit(false);
        SingleConnectionDataSource single = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(single);
        store = new FakeHealthStore(new AnalyticsDatabase(single, false));
        clock = new MutableClock(NOW);
        runs = new FakeRuns();
        gateway = new FakeGateway(base);
        for (String job : VisitDiagnosticsAdminService.CRON_JOBS) { // 기본: 모두 5분마다 · 켜짐 · 1분 전에 성공
            gateway.jobs.put(job, new DbBatchGateway.DbJob(job, "*/5 * * * *", 1L, "*/5 * * * *", true));
            runs.latest.put(job, run(job, BatchRunStatus.OK, NOW.minusSeconds(60)));
        }
    }

    @AfterEach
    void close() throws Exception {
        connection.rollback();
        connection.close();
    }

    // ── 가짜 ─────────────────────────────────────────────────────────────────

    /** 진단 표는 실제 DB, 원본 보관(카탈로그 · 다음 달 조각은 DB 의 지금 시각으로 본다)만 정해 둔 값 */
    static final class FakeHealthStore extends VisitHealthStore {

        VisitHealthStore.RawRow raw = new VisitHealthStore.RawRow(2, 1000L, NOW.minus(Duration.ofDays(10)), true);

        FakeHealthStore(AnalyticsDatabase db) {
            super(db);
        }

        @Override
        public RawRow raw() {
            return raw;
        }
    }

    static final class FakeRuns extends BatchRunRepositoryQDSL {

        final Map<String, BatchRunEntity> latest = new HashMap<>();

        FakeRuns() {
            super(null);
        }

        @Override
        public Map<String, BatchRunEntity> latestByJobKey(Collection<String> jobKeys) {
            return latest;
        }
    }

    static final class FakeGateway extends DbBatchGateway {

        final Map<String, DbJob> jobs = new HashMap<>();
        PgCronState state = new PgCronState("READY", "Asia/Seoul");

        FakeGateway(DataSource ds) {
            super(new PgCronQueries(ds));
        }

        @Override
        public PgCronState state() {
            return state;
        }

        @Override
        public Map<String, DbJob> jobs(boolean cronReady) {
            if (cronReady) {
                return jobs;
            }
            Map<String, DbJob> plain = new HashMap<>();
            jobs.forEach((k, j) -> plain.put(k, new DbJob(k, j.defaultCron(), null, null, false)));
            return plain;
        }
    }

    static BatchRunEntity run(String job, BatchRunStatus status, Instant startedAt) {
        return new BatchRunEntity() {
            @Override
            public String getJobKey() {
                return job;
            }

            @Override
            public BatchRunStatus getStatus() {
                return status;
            }

            @Override
            public Instant getStartedAt() {
                return startedAt;
            }

            @Override
            public Long getAffectedCount() {
                return 3L;
            }

            @Override
            public String getErrorMessage() {
                return status == BatchRunStatus.FAIL ? "SQLSTATE XX000: qa" : null;
            }

            @Override
            public String getMessageKey() {
                return null;
            }

            @Override
            public String getMessageArgs() {
                return null;
            }
        };
    }

    private VisitDiagnosticsAdminService service() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("clock", clock);
        StatsCollectionSettings settings = new StatsCollectionSettings(null, null) {
            @Override
            public StatsCollection current() {
                return current;
            }
        };
        return new VisitDiagnosticsAdminService(store, settings, runs, gateway, new BatchCronPolicy(null), new FakeMessages(), beans.getBeanProvider(Clock.class));
    }

    static Instant kst(int m, int d, int h, int min, int s) {
        return LocalDateTime.of(2098, m, d, h, min, s).atZone(ServiceTime.ZONE).toInstant();
    }

    private void beat(String instance, Instant at) {
        jdbc.update("INSERT INTO analytics_collector_beats (instance, last_write_at) VALUES (?, ?)", instance, Timestamp.from(at));
    }

    private void hourly(String instance, Instant hour, long ok, long fail, long dropped, long bot, long invalid) {
        jdbc.update("INSERT INTO analytics_collector_hourly (hour, instance, flush_ok, flush_fail, dropped, ignored_bot, ignored_invalid) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Timestamp.from(hour.truncatedTo(ChronoUnit.HOURS)), instance, ok, fail, dropped, bot, invalid);
    }

    private void failure(String instance, Instant at, String reason) {
        jdbc.update("INSERT INTO analytics_collector_failures (failed_at, instance, reason, detail) VALUES (?, ?, ?, 'qa')", Timestamp.from(at), instance, reason);
    }

    private static List<String> codes(VisitAnalyticsAdmin.Health h) {
        return h.issues().stream().map(i -> i.code() + ":" + i.level() + (i.instance() == null ? "" : ":" + i.instance()) + (i.job() == null ? "" : ":" + i.job())).toList();
    }

    // ── 상태 · 서버 ─────────────────────────────────────────────────────────

    @Test
    void 모두_정상이면_OK_이고_서버별_24시간_합을_이름_차례로() {
        beat("qa-b", NOW.minusSeconds(30));
        beat("qa-a", NOW.minusSeconds(50));
        hourly("qa-a", NOW, 50, 0, 0, 3, 1);
        hourly("qa-a", NOW.minus(Duration.ofHours(5)), 60, 0, 0, 2, 0);
        hourly("qa-a", NOW.minus(Duration.ofHours(25)), 1000, 1000, 1000, 1000, 1000); // 24시간 밖
        hourly("qa-b", NOW, 10, 0, 0, 0, 0);

        VisitAnalyticsAdmin.Health h = service().health();

        assertThat(h.status()).isEqualTo("OK");
        assertThat(h.issues()).isEmpty();
        assertThat(h.instances()).containsExactly(new VisitAnalyticsAdmin.HealthInstance("qa-a", NOW.minusSeconds(50), 110, 0, 0, 5, 1),
                new VisitAnalyticsAdmin.HealthInstance("qa-b", NOW.minusSeconds(30), 10, 0, 0, 0, 0));
        assertThat(h.crons()).hasSize(VisitDiagnosticsAdminService.CRON_JOBS.size()).allMatch(c -> !c.late() && "OK".equals(c.lastStatus()));
        assertThat(h.raw()).isEqualTo(new VisitAnalyticsAdmin.HealthRaw(true, 90, ServiceTime.dateOf(NOW.minus(Duration.ofDays(10))).toString(), 2, 1000L, true));
        assertThat(h.asOf()).isEqualTo(NOW);
    }

    @Test
    void 마지막_기록이_3분_넘으면_WARN_10분_넘으면_ERROR_경계() {
        beat("qa-1-live", NOW.minus(Duration.ofMinutes(3)).plusSeconds(1));
        beat("qa-2-3min", NOW.minus(Duration.ofMinutes(3)));
        beat("qa-3-9min", NOW.minus(Duration.ofMinutes(10)).plusSeconds(1));
        beat("qa-4-10min", NOW.minus(Duration.ofMinutes(10)));
        beat("qa-5-gone", NOW.minus(Duration.ofHours(24)).minusSeconds(1)); // 24시간 전에 멈춘 서버는 목록에서 빠진다

        VisitAnalyticsAdmin.Health h = service().health();

        assertThat(h.status()).isEqualTo("DELAYED");
        assertThat(codes(h)).containsExactly("INSTANCE_SILENT:ERROR:qa-4-10min", "INSTANCE_SILENT:WARN:qa-2-3min", "INSTANCE_SILENT:WARN:qa-3-9min");
        assertThat(h.issues().get(0).since()).isEqualTo(NOW.minus(Duration.ofMinutes(10)));
        assertThat(h.instances()).extracting(VisitAnalyticsAdmin.HealthInstance::instance).containsExactly("qa-1-live", "qa-2-3min", "qa-3-9min", "qa-4-10min");
    }

    @Test
    void 지난_3분_안에_기록한_서버가_하나도_없으면_STOPPED_서버가_아예_없어도_STOPPED() {
        assertThat(service().health().status()).isEqualTo("STOPPED");
        beat("qa-silent", NOW.minus(Duration.ofMinutes(4)));
        VisitAnalyticsAdmin.Health h = service().health();
        assertThat(h.status()).isEqualTo("STOPPED");
        assertThat(codes(h)).containsExactly("INSTANCE_SILENT:WARN:qa-silent");
    }

    @Test
    void 수집이_모두_꺼지면_서버가_멈췄어도_OFF_이고_COLLECT_OFF() {
        current = new StatsCollection(true, false, false, 90, false, false, false, false, false, true, true);
        VisitAnalyticsAdmin.Health h = service().health();
        assertThat(h.status()).isEqualTo("OFF");
        assertThat(codes(h)).containsExactly("COLLECT_OFF:WARN");

        current = new StatsCollection(false, true, true, 90, true, true, true, true, true, false, false);
        assertThat(service().health().status()).as("전체가 꺼지면 하위 칸 값과 상관없이").isEqualTo("OFF");

        current = new StatsCollection(true, false, false, 90, false, false, false, false, true, false, false); // 봇만
        beat("qa-live", NOW.minusSeconds(10));
        assertThat(service().health().status()).isEqualTo("OK");
    }

    // ── 기록 실패 · 버린 신호 ───────────────────────────────────────────────

    @Test
    void 기록_실패는_24시간_안이면_WARN_지금_시간_칸이면_ERROR_24시간_밖은_안_센다() {
        beat("qa-old-fail", NOW.minusSeconds(20));
        beat("qa-now-fail", NOW.minusSeconds(20));
        beat("qa-ancient", NOW.minusSeconds(20));
        hourly("qa-old-fail", NOW.minus(Duration.ofHours(5)), 10, 2, 0, 0, 0);
        hourly("qa-now-fail", NOW, 10, 1, 0, 0, 0);
        hourly("qa-ancient", NOW.minus(Duration.ofHours(25)), 10, 9, 0, 0, 0);
        failure("qa-now-fail", NOW.minusSeconds(100), "DB_TIMEOUT");
        failure("qa-old-fail", NOW.minus(Duration.ofHours(5)), "DB_UNAVAILABLE");
        failure("qa-ancient", NOW.minus(Duration.ofHours(25)), "UNKNOWN");

        VisitAnalyticsAdmin.Health h = service().health();

        assertThat(codes(h)).containsExactly("FLUSH_FAILED:ERROR:qa-now-fail", "FLUSH_FAILED:WARN:qa-old-fail");
        assertThat(h.issues().get(1).count()).isEqualTo(2);
        assertThat(h.failures()).extracting(VisitAnalyticsAdmin.HealthFailure::instance).containsExactly("qa-now-fail", "qa-old-fail");
        assertThat(h.status()).isEqualTo("DELAYED");
    }

    @Test
    void 지난_1시간_안의_실패가_앞_시간_칸에_있어도_ERROR() {
        // 지금 10:20:30. 30분 전(09:50)의 기록 실패는 「지난 1시간」이라 계약대로면 ERROR 다.
        // 시간 칸(09:00)은 09:20:30 보다 앞이라 칸으로는 빠지지만, 실패 기록의 정확한 시각(09:50)으로 잡는다(QA 2026-11-04 재현 → 수정).
        beat("qa-recent", NOW.minusSeconds(20));
        hourly("qa-recent", kst(5, 20, 9, 0, 0), 10, 1, 0, 0, 0);
        failure("qa-recent", kst(5, 20, 9, 50, 0), "DB_TIMEOUT");

        VisitAnalyticsAdmin.Health h = service().health();

        assertThat(codes(h)).containsExactly("FLUSH_FAILED:ERROR:qa-recent");
    }

    @Test
    void 버린_신호가_있으면_WARN_과_버린_수() {
        beat("qa-drop", NOW.minusSeconds(20));
        hourly("qa-drop", NOW, 10, 0, 7, 0, 0);
        hourly("qa-drop", NOW.minus(Duration.ofHours(2)), 10, 0, 3, 0, 0);
        VisitAnalyticsAdmin.Health h = service().health();
        assertThat(codes(h)).containsExactly("BUFFER_DROPPED:WARN:qa-drop");
        assertThat(h.issues().get(0).count()).isEqualTo(10);
    }

    // ── 크론 ─────────────────────────────────────────────────────────────────

    @Test
    void 크론_마지막_실행이_실패면_ERROR_예정_두_번_지나도_안_돌면_WARN() {
        beat("qa-live", NOW.minusSeconds(10));
        runs.latest.put(FORGET_JOB, run(FORGET_JOB, BatchRunStatus.FAIL, NOW.minusSeconds(30)));
        // 5분마다 — 마지막 10:10:29 → 10:15 · 10:20 을 지나쳤다(지금 10:20:30)
        runs.latest.put("analytics-log-cleanup", run("analytics-log-cleanup", BatchRunStatus.OK, kst(5, 20, 10, 10, 29)));
        // 마지막 10:15:01 → 10:20 하나만 지났다
        runs.latest.put("analytics-visit-mark-cleanup", run("analytics-visit-mark-cleanup", BatchRunStatus.SKIP, kst(5, 20, 10, 15, 1)));

        VisitAnalyticsAdmin.Health h = service().health();

        assertThat(codes(h)).containsExactly("CRON_FAILED:ERROR:" + FORGET_JOB, "CRON_LATE:WARN:analytics-log-cleanup");
        VisitAnalyticsAdmin.HealthCron failed = h.crons().stream().filter(c -> c.job().equals(FORGET_JOB)).findFirst().orElseThrow();
        assertThat(failed.lastStatus()).isEqualTo("FAIL");
        assertThat(failed.message()).isEqualTo("SQLSTATE XX000: qa");
        assertThat(failed.processed()).isEqualTo(3);
        assertThat(h.crons().stream().filter(c -> c.job().equals("analytics-visit-mark-cleanup")).findFirst().orElseThrow().late()).isFalse();
    }

    @Test
    void 크론_늦음은_두_번째_예정_시각을_넘었을_때부터() {
        beat("qa-live", NOW.minusSeconds(10));
        runs.latest.put("analytics-log-cleanup", run("analytics-log-cleanup", BatchRunStatus.OK, kst(5, 20, 10, 10, 0)));
        clock.now = kst(5, 20, 10, 20, 0); // 두 번째 예정(10:20) 바로 그 시각 — 아직 늦지 않음
        beat("qa-live2", clock.now.minusSeconds(5));
        assertThat(codes(service().health())).isEmpty();
        clock.now = kst(5, 20, 10, 20, 1);
        assertThat(codes(service().health())).containsExactly("CRON_LATE:WARN:analytics-log-cleanup");
    }

    @Test
    void 등록되지_않았거나_꺼졌거나_pg_cron_을_못_쓰면_늦음_판정이_없다() {
        beat("qa-live", NOW.minusSeconds(10));
        Instant old = NOW.minus(Duration.ofDays(3));
        runs.latest.put("analytics-log-cleanup", run("analytics-log-cleanup", BatchRunStatus.OK, old));
        runs.latest.put(FORGET_JOB, run(FORGET_JOB, BatchRunStatus.OK, old));
        gateway.jobs.put("analytics-log-cleanup", new DbBatchGateway.DbJob("analytics-log-cleanup", "45 0 * * *", null, null, false)); // 등록 안 됨
        gateway.jobs.put(FORGET_JOB, new DbBatchGateway.DbJob(FORGET_JOB, "*/5 * * * *", 7L, "*/5 * * * *", false)); // 꺼짐
        VisitAnalyticsAdmin.Health h = service().health();
        assertThat(codes(h)).isEmpty();
        assertThat(h.crons().stream().filter(c -> c.job().equals("analytics-log-cleanup")).findFirst().orElseThrow().cron()).as("등록 안 됨 — 기본 식")
                .isEqualTo("45 0 * * *");

        gateway.jobs.put(FORGET_JOB, new DbBatchGateway.DbJob(FORGET_JOB, "*/5 * * * *", 7L, "*/5 * * * *", true));
        gateway.state = new DbBatchGateway.PgCronState("NOT_INSTALLED", "GMT");
        assertThat(codes(service().health())).isEmpty();
        runs.latest.clear(); // 한 번도 안 돈 크론
        gateway.state = new DbBatchGateway.PgCronState("READY", "Asia/Seoul");
        VisitAnalyticsAdmin.Health never = service().health();
        assertThat(codes(never)).isEmpty();
        assertThat(never.crons()).allMatch(c -> c.lastRunAt() == null && c.lastStatus() == null && !c.late());
    }

    // ── 원본 보관 ───────────────────────────────────────────────────────────

    @Test
    void 가장_오래된_원본이_보관_일수_더하기_1일을_넘으면_넘친_날_수로_WARN() {
        beat("qa-live", NOW.minusSeconds(10));
        LocalDate today = ServiceTime.dateOf(NOW);
        store.raw = new VisitHealthStore.RawRow(4, null, today.minusDays(91).atTime(0, 30).atZone(ServiceTime.ZONE).toInstant(), true);
        assertThat(codes(service().health())).as("90일 + 1일 — 정리가 하루 늦은 것까지는 정상").isEmpty();

        store.raw = new VisitHealthStore.RawRow(4, null, today.minusDays(92).atTime(23, 59).atZone(ServiceTime.ZONE).toInstant(), true);
        clock.now = NOW.plusSeconds(21);
        beat("qa-live2", clock.now.minusSeconds(5));
        VisitAnalyticsAdmin.Health h = service().health();
        assertThat(codes(h)).containsExactly("RAW_BACKLOG:WARN");
        assertThat(h.issues().get(0).count()).isEqualTo(1);
        assertThat(h.raw().approxRows()).isNull();
        assertThat(h.raw().oldestDay()).isEqualTo(today.minusDays(92).toString());

        current = new StatsCollection(true, true, true, 30, true, true, true, true, true, false, false);
        assertThat(service().health().issues().get(0).count()).as("보관 30일이면 92 − 31").isEqualTo(61);
    }

    @Test
    void 다음_달_조각이_없으면_WARN_그_달_마지막_3일이면_ERROR_원본_기록이_꺼졌으면_없음() {
        store.raw = new VisitHealthStore.RawRow(1, 0L, null, false);
        gateway.state = new DbBatchGateway.PgCronState("NOT_INSTALLED", "GMT"); // 시계를 날 단위로 옮긴다 — 크론 늦음 판정은 빼고 본다
        for (Object[] c : new Object[][] {{28, "WARN"}, {29, "ERROR"}, {31, "ERROR"}, {1, "WARN"}}) {
            Instant at = kst(5, (Integer) c[0], 12, 0, 0);
            clock.now = at;
            beat("qa-live-" + c[0], at.minusSeconds(5));
            assertThat(codes(service().health())).as("5월 %s일", c[0]).containsExactly("NEXT_PARTITION_MISSING:" + c[1]);
        }
        clock.now = kst(2, 26, 12, 0, 0); // 2098년 2월은 28일 — 26일이면 남은 날 3
        beat("qa-live-feb", clock.now.minusSeconds(5));
        assertThat(codes(service().health())).containsExactly("NEXT_PARTITION_MISSING:ERROR");

        current = new StatsCollection(true, true, false, 90, true, true, true, true, true, false, false);
        clock.now = kst(2, 26, 12, 0, 30);
        assertThat(codes(service().health())).isEmpty();
    }

    @Test
    void 원본_보관_읽기는_조각_수_가장_오래된_시각_다음_달_조각을_카탈로그로_본다() {
        VisitHealthStore real = new VisitHealthStore(new AnalyticsDatabase(new SingleConnectionDataSource(connection, true), false));
        VisitHealthStore.RawRow before = real.raw();
        jdbc.execute("CREATE TABLE qa_health_pv_1999_12 PARTITION OF analytics_page_views FOR VALUES FROM ('1999-12-01 00:00:00+09') TO ('2000-01-01 00:00:00+09')");
        Instant oldest = Instant.parse("1999-12-05T01:02:03Z");
        jdbc.update("INSERT INTO analytics_page_views (id, viewed_at, visitor_hash, session_hash, path, kind, device, new_visitor) "
                + "VALUES (98000100, ?, 'qa-h', 'qa-s', '/', 'HOME', 'DESKTOP', false)", Timestamp.from(oldest));
        VisitHealthStore.RawRow after = real.raw();
        assertThat(after.partitions()).isEqualTo(before.partitions() + 1);
        assertThat(after.oldest()).isEqualTo(oldest);

        String next = jdbc.queryForObject("SELECT format('analytics_page_views_%s', to_char(date_trunc('month', now() AT TIME ZONE 'Asia/Seoul') + interval '1 month', 'YYYY_MM'))",
                String.class);
        boolean exists = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, next);
        if (exists) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + next, Long.class)).isZero();
            jdbc.execute("DROP TABLE " + next); // 시험 트랜잭션과 함께 되돌린다
        }
        assertThat(real.raw().nextPartitionReady()).isFalse();
        jdbc.execute("CREATE TABLE " + next + " PARTITION OF analytics_page_views FOR VALUES FROM ('2200-01-01') TO ('2200-02-01')");
        assertThat(real.raw().nextPartitionReady()).isTrue();
    }

    // ── 정렬 ─────────────────────────────────────────────────────────────────

    @Test
    void 문제는_ERROR_먼저_같으면_코드_차례() {
        beat("qa-live", NOW.minusSeconds(10));
        beat("qa-silent", NOW.minus(Duration.ofMinutes(5)));
        hourly("qa-live", NOW, 10, 1, 4, 0, 0);
        runs.latest.put(FORGET_JOB, run(FORGET_JOB, BatchRunStatus.FAIL, NOW.minusSeconds(30)));
        store.raw = new VisitHealthStore.RawRow(1, 0L, NOW.minus(Duration.ofDays(200)), false);
        clock.now = NOW; // 5월 20일 — 다음 달 조각 없음은 WARN

        VisitAnalyticsAdmin.Health h = service().health();

        assertThat(codes(h)).containsExactly("FLUSH_FAILED:ERROR:qa-live", "CRON_FAILED:ERROR:" + FORGET_JOB, "INSTANCE_SILENT:WARN:qa-silent",
                "BUFFER_DROPPED:WARN:qa-live", "RAW_BACKLOG:WARN", "NEXT_PARTITION_MISSING:WARN");
        assertThat(h.status()).isEqualTo("DELAYED");
    }

    @Test
    void 수집_진단은_20초_캐시() {
        beat("qa-live", NOW.minusSeconds(10));
        VisitDiagnosticsAdminService svc = service();
        assertThat(svc.health().instances()).hasSize(1);
        beat("qa-second", NOW.minusSeconds(5));
        clock.now = NOW.plusSeconds(19);
        assertThat(svc.health().instances()).hasSize(1);
        clock.now = NOW.plusSeconds(21);
        assertThat(svc.health().instances()).hasSize(2);
        assertThat(VisitDiagnosticsAdminService.HEALTH_TTL).isEqualTo(Duration.ofSeconds(20));
    }

    // ── 정보주체 요청 목록 ──────────────────────────────────────────────────

    private void request(long id, Instant receivedAt, String status, Long deleted, Instant processedAt) {
        jdbc.update("INSERT INTO analytics_forget_requests (id, visitor_hash, received_at, status, deleted_rows, processed_at) VALUES (?, ?, ?, ?, ?, ?)", id,
                "PENDING".equals(status) ? "qa-h-" + id : null, Timestamp.from(receivedAt), status, deleted, processedAt == null ? null : Timestamp.from(processedAt));
    }

    @Test
    void 요청_목록은_최신_차례_쪽_넘김과_대기_수_지난_30일_수() {
        long total0 = jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests", Long.class);
        long pending0 = jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests WHERE status = 'PENDING'", Long.class);
        request(98_000_001L, NOW.minus(Duration.ofDays(40)), "DONE", 5L, NOW.minus(Duration.ofDays(40)).plusSeconds(300));
        request(98_000_002L, NOW.minus(Duration.ofDays(30)).plusSeconds(1), "DONE", 0L, NOW.minus(Duration.ofDays(29)));
        request(98_000_003L, NOW.minus(Duration.ofMinutes(10)), "DONE", 12L, NOW.minus(Duration.ofMinutes(5)));
        request(98_000_004L, NOW.minus(Duration.ofMinutes(1)), "PENDING", 3L, null); // 처리 중 누적 — 대기 중에는 보이지 않는다
        request(98_000_005L, NOW.minus(Duration.ofMinutes(1)), "PENDING", null, null); // 같은 시각이면 번호 큰 쪽이 위

        VisitAnalyticsAdmin.ForgetPage p = service().requests(null, "2");

        assertThat(p.total()).isEqualTo(total0 + 5);
        assertThat(p.pending()).isEqualTo(pending0 + 2);
        assertThat(p.last30Days()).isEqualTo(4);
        assertThat(p.items()).containsExactly(new VisitAnalyticsAdmin.ForgetRequest("98000005", NOW.minus(Duration.ofMinutes(1)), "PENDING", null, null),
                new VisitAnalyticsAdmin.ForgetRequest("98000004", NOW.minus(Duration.ofMinutes(1)), "PENDING", null, null));
        VisitAnalyticsAdmin.ForgetPage p2 = service().requests("1", "2");
        assertThat(p2.items()).extracting(VisitAnalyticsAdmin.ForgetRequest::id).containsExactly("98000003", "98000002");
        assertThat(p2.items().get(0)).isEqualTo(new VisitAnalyticsAdmin.ForgetRequest("98000003", NOW.minus(Duration.ofMinutes(10)), "DONE", 12L,
                NOW.minus(Duration.ofMinutes(5))));
        assertThat(service().requests("10000", "100").items()).as("끝 뒤 쪽 — 빈 목록, 수는 그대로").isEmpty();
        assertThat(service().requests("10000", "100").total()).isEqualTo(total0 + 5);
        assertThat(service().requests(null, null).items()).hasSize((int) Math.min(20, total0 + 5));
    }

    @Test
    void 요청_목록의_쪽_크기가_틀리면_400_칸별() {
        VisitDiagnosticsAdminService svc = service();
        assertThat(fields(() -> svc.requests("-1", null))).containsOnlyKeys("page");
        assertThat(fields(() -> svc.requests("x", null))).containsOnlyKeys("page");
        assertThat(fields(() -> svc.requests("10001", null))).containsOnlyKeys("page");
        assertThat(fields(() -> svc.requests(null, "0"))).containsOnlyKeys("size");
        assertThat(fields(() -> svc.requests(null, "101"))).containsOnlyKeys("size");
        assertThat(fields(() -> svc.requests("a", "b"))).containsOnlyKeys("page", "size");
        svc.requests("10000", "100");
    }

    @Test
    void 요청_목록은_60초_캐시() {
        VisitDiagnosticsAdminService svc = service();
        long before = svc.requests(null, null).total();
        request(98_000_010L, NOW.minusSeconds(5), "PENDING", null, null);
        clock.now = NOW.plusSeconds(59);
        assertThat(svc.requests(null, null).total()).isEqualTo(before);
        clock.now = NOW.plusSeconds(61);
        assertThat(svc.requests(null, null).total()).isEqualTo(before + 1);
        assertThat(svc.requests(null, null).pending()).isGreaterThanOrEqualTo(1);
    }

    private static Map<String, ?> fields(Runnable call) {
        try {
            call.run();
        } catch (ValidationException e) {
            return e.getFields();
        }
        throw new AssertionError("ValidationException 이 나야 한다");
    }
}
