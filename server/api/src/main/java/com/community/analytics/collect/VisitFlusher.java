package com.community.analytics.collect;

import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.AnalyticsWriteStore;
import com.community.analytics.store.AnalyticsWriteStore.CampaignRow;
import com.community.analytics.store.AnalyticsWriteStore.DailyRow;
import com.community.analytics.store.AnalyticsWriteStore.Diagnostics;
import com.community.analytics.store.AnalyticsWriteStore.EnvRow;
import com.community.analytics.store.AnalyticsWriteStore.FailureRow;
import com.community.analytics.store.AnalyticsWriteStore.RawRow;
import com.community.analytics.store.AnalyticsWriteStore.SearchRow;
import com.community.analytics.store.AnalyticsWriteStore.VisitorRow;
import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 방문 신호 몰아쓰기 — <b>1분마다(그리고 서버가 내려갈 때)</b> 이 서버의 메모리 모음({@link VisitBuffer})을 통째로 가져와 분석 DB 에 쓴다. 자바 배치가 아니라 서버마다의 쓰기 버퍼다
 * (정리·파티션 같은 주기 작업은 pg_cron — R__analytics_*).
 *
 * <p>한 번에 쓰는 문장(켜진 항목만, 빈 것은 건너뜀 — 문장 수는 방문량과 무관하다):
 * <ol>
 *   <li>방문자 + 페이지뷰: 표시 넣기 {@code ON CONFLICT DO NOTHING RETURNING} → 실제로 들어간 줄만 시간 칸 방문자·신규 방문자, 같은 문장에서 페이지뷰 더하기 upsert (1문장)</li>
 *   <li>지금 접속 upsert (1문장) — 1·2 는 한 트랜잭션</li>
 *   <li>원본 여러 줄 (1문장) + 페이지별 일별 조회 더하기 (1문장) — 한 트랜잭션. 그 달 조각이 없으면 한 번 만들고 다시 쓴다</li>
 *   <li>유입처 일별 더하기 (1문장) + 캠페인 일별 더하기 (1문장, analytics V3) — 한 트랜잭션</li>
 *   <li>이동 일별 더하기 (1문장) — 세션 첫 페이지의 「바로 들어옴」(from_path = '') 줄 포함</li>
 *   <li>2단계(analytics V4): 환경 일별 (1문장) · 검색어 일별 + 걸러진 검색 수 (2문장, 한 트랜잭션) · 봇 일별 (1문장)</li>
 *   <li><b>수집 진단 (1문장, 늘)</b>: 이 서버의 「살아 있음」 + 시간 칸(이번 1분의 기록 성공·실패 수 · 버린 줄 · 무시한 신호) + 기록 실패 줄. 신호가 없어도 쓴다 —
 *       관리자 「수집 진단」이 서버가 멈췄는지 이것으로 안다. 진단 쓰기마저 실패하면 숫자를 메모리에 두었다가 다음 1분에 함께 쓴다.</li>
 * </ol>
 * 서버 여러 대의 정확성 근거는 {@link AnalyticsWriteStore} 머리말. 묶음 하나가 실패하면(트랜잭션째 되돌려졌다) 그 묶음만 메모리에 되돌려 다음 1분에 다시 쓴다(상한은 버퍼가 지킨다).
 * 서버가 갑자기 꺼지면 아직 쓰지 않은 최대 1분치를 잃는다(통계라 허용 — 계약). 「오늘 셌다」 표시는 DB 에 있어 재시작해도 같은 방문자를 다시 세지 않는다.
 *
 * <p>서버 이름(진단의 {@code instance}): 설정 {@code roboyam.analytics.instance-name}, 없으면 {@code api-<NODE_ID>} — 서버마다 다른 NODE_ID 를 쓰므로(Snowflake 규칙) 겹치지 않고,
 * 컨테이너를 새로 띄워도 같은 이름이라 「멈춘 서버」가 남지 않는다.
 */
@Component
public class VisitFlusher {

    private static final Logger log = LoggerFactory.getLogger(VisitFlusher.class);

    /** PostgreSQL 「행에 맞는 조각이 없다」(check_violation) */
    private static final String NO_PARTITION = "23514";
    /** 메모리에 쌓아 두는 기록 실패 줄 상한(진단 쓰기가 계속 실패할 때) */
    static final int MAX_PENDING_FAILURES = 50;
    static final int INSTANCE_MAX = 64;
    static final int DETAIL_MAX = 200;

    private final VisitCollector collector;
    private final AnalyticsWriteStore store;
    private final AnalyticsDatabase db;
    private final String instance;

    // 아직 쓰지 못한 수집 진단(flush 는 synchronized 라 한 번에 하나만 만진다)
    private long pendingOk;
    private long pendingFail;
    private long pendingDropped;
    private long pendingIgnoredBot;
    private long pendingIgnoredInvalid;
    private final ArrayDeque<FailureRow> pendingFailures = new ArrayDeque<>();

    @Autowired
    public VisitFlusher(VisitCollector collector, AnalyticsWriteStore store, AnalyticsDatabase db, @Value("${roboyam.analytics.instance-name:}") String instanceName,
                        @Value("${roboyam.node-id:0}") int nodeId) {
        this.collector = collector;
        this.store = store;
        this.db = db;
        String name = instanceName == null || instanceName.isBlank() ? "api-" + nodeId : instanceName.strip();
        this.instance = name.length() > INSTANCE_MAX ? name.substring(0, INSTANCE_MAX) : name;
    }

    /** 시험용 — 서버 이름 {@code api-0} */
    VisitFlusher(VisitCollector collector, AnalyticsWriteStore store, AnalyticsDatabase db) {
        this(collector, store, db, null, 0);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public synchronized void flush() {
        VisitBuffer buffer = collector.buffer();
        VisitBuffer.Batch b = buffer.drain();
        pendingDropped += b.dropped();
        pendingIgnoredBot += b.ignoredBot();
        pendingIgnoredInvalid += b.ignoredInvalid();
        if (!b.isEmpty()) {
            write(buffer, b);
        }
        diagnostics();
    }

    private void write(VisitBuffer buffer, VisitBuffer.Batch b) {
        if (!b.visitors().isEmpty() || !b.pageViews().isEmpty() || !b.active().isEmpty()) {
            List<VisitorRow> visitors = new ArrayList<>(b.visitors().size());
            b.visitors().forEach((k, v) -> visitors.add(new VisitorRow(k.day(), k.visitorHash(), v.hour(), v.newVisitor())));
            if (!step("방문자", () -> store.visits(visitors, b.pageViews(), b.active()))) {
                buffer.restoreVisits(b);
            }
        }
        if (!b.raw().isEmpty() || !b.pages().isEmpty()) {
            List<RawRow> raw = new ArrayList<>(b.raw().size());
            b.raw().forEach(r -> raw.add(new RawRow(r.id(), r.viewedAt(), r.visitorHash(), r.sessionHash(), r.path(), r.kind(), r.prevPath(), r.referrerDomain(),
                    r.referrerGroup(), r.utmSource(), r.utmMedium(), r.utmCampaign(), r.device(), r.newVisitor())));
            List<DailyRow> pages = new ArrayList<>(b.pages().size());
            b.pages().forEach((k, n) -> pages.add(new DailyRow(k.day(), k.path(), k.kind(), n)));
            Runnable writeRaw = () -> {
                store.raw(raw);
                store.pages(pages);
            };
            RuntimeException failure = run(writeRaw);
            if (failure != null && sqlState(failure, NO_PARTITION) && run(store::ensurePartitions) == null) {
                failure = run(writeRaw); // 조각이 없어 거절됐다 — 한 번 만들고 다시(성공하면 실패로 세지 않는다)
            }
            if (!done("원본", failure)) {
                buffer.restoreRaw(b);
            }
        }
        if (!b.referrers().isEmpty() || !b.campaigns().isEmpty()) {
            List<DailyRow> rows = new ArrayList<>(b.referrers().size());
            b.referrers().forEach((k, n) -> rows.add(new DailyRow(k.day(), k.group(), k.domain(), n)));
            List<CampaignRow> campaigns = new ArrayList<>(b.campaigns().size());
            b.campaigns().forEach((k, n) -> campaigns.add(new CampaignRow(k.day(), k.source(), k.medium(), k.campaign(), n)));
            if (!step("유입처", () -> {
                store.referrers(rows);
                store.campaigns(campaigns);
            })) {
                buffer.restoreReferrers(b);
            }
        }
        if (!b.flows().isEmpty()) {
            List<DailyRow> rows = new ArrayList<>(b.flows().size());
            b.flows().forEach((k, n) -> rows.add(new DailyRow(k.day(), k.from(), k.to(), n)));
            if (!step("이동", () -> store.flows(rows))) {
                buffer.restoreFlows(b);
            }
        }
        if (!b.env().isEmpty()) {
            List<EnvRow> rows = new ArrayList<>(b.env().size());
            b.env().forEach((k, n) -> rows.add(new EnvRow(k.day(), k.device(), k.browser(), k.os(), k.screen(), n)));
            if (!step("환경", () -> store.env(rows))) {
                buffer.restoreEnv(b);
            }
        }
        if (!b.searches().isEmpty() || !b.searchFiltered().isEmpty()) {
            List<SearchRow> rows = new ArrayList<>(b.searches().size());
            b.searches().forEach((k, c) -> rows.add(new SearchRow(k.day(), k.term(), c.searches(), c.noResult())));
            if (!step("검색어", () -> store.searches(rows, b.searchFiltered()))) {
                buffer.restoreSearches(b);
            }
        }
        if (!b.bots().isEmpty()) {
            List<DailyRow> rows = new ArrayList<>(b.bots().size());
            b.bots().forEach((k, n) -> rows.add(new DailyRow(k.day(), k.kind(), "", n)));
            if (!step("봇", () -> store.bots(rows))) {
                buffer.restoreBots(b);
            }
        }
    }

    /** 수집 진단 한 문장 — 실패하면 숫자를 남겨 두고 다음 1분에 더해서 쓴다 */
    private void diagnostics() {
        Instant now = Instant.now();
        Diagnostics d = new Diagnostics(instance, now, now.truncatedTo(ChronoUnit.HOURS), pendingOk, pendingFail, pendingDropped, pendingIgnoredBot,
                pendingIgnoredInvalid, List.copyOf(pendingFailures));
        RuntimeException failure = run(() -> store.diagnostics(d));
        if (failure == null) {
            pendingOk = 0;
            pendingFail = 0;
            pendingDropped = 0;
            pendingIgnoredBot = 0;
            pendingIgnoredInvalid = 0;
            pendingFailures.clear();
            return;
        }
        log.warn("방문 수집 진단을 쓰지 못해 다음 1분에 함께 쓴다: {}", failure.getClass().getSimpleName());
        remember(now, "수집 진단", failure);
    }

    @PreDestroy
    void shutdown() {
        flush();
    }

    /** 한 묶음 쓰기 — 실패는 경고·진단에 남기고 false(방문 신호 받기에는 영향이 없다) */
    private boolean step(String what, Runnable work) {
        return done(what, run(work));
    }

    private boolean done(String what, RuntimeException failure) {
        if (failure == null) {
            pendingOk++;
            return true;
        }
        pendingFail++;
        log.warn("방문 {} 몰아쓰기에 실패해 다음 1분에 다시 쓴다: {}", what, failure.getClass().getSimpleName());
        remember(Instant.now(), what, failure);
        return false;
    }

    /** 분석 연결의 트랜잭션 하나로 — 실패하면 그 예외(되돌려졌다), 성공하면 null */
    private RuntimeException run(Runnable work) {
        try {
            db.inTransaction(() -> {
                work.run();
                return null;
            });
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    /** 기록 실패 한 줄(사유 코드 + 예외 이름·SQLSTATE·묶음 이름 — 값·문장은 남기지 않는다) */
    private void remember(Instant at, String what, RuntimeException e) {
        String state = sqlStateOf(e);
        String detail = e.getClass().getSimpleName() + (state == null ? "" : " (SQLSTATE " + state + ")") + " — " + what;
        pendingFailures.addLast(new FailureRow(at, reason(e, state), detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail));
        while (pendingFailures.size() > MAX_PENDING_FAILURES) {
            pendingFailures.pollFirst();
        }
    }

    /** 사유 코드 — DB_TIMEOUT · DB_UNAVAILABLE · CONSTRAINT · UNKNOWN(화면 types {@code VisitHealthFailure.reason}) */
    static String reason(Throwable e, String sqlState) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof QueryTimeoutException || t instanceof SQLTimeoutException) {
                return "DB_TIMEOUT";
            }
            if (t instanceof DataAccessResourceFailureException) {
                return "DB_UNAVAILABLE";
            }
            if (t instanceof DataIntegrityViolationException) {
                return "CONSTRAINT";
            }
        }
        if (sqlState != null) {
            if (sqlState.equals("57014") || sqlState.equals("55P03")) {
                return "DB_TIMEOUT";
            }
            if (sqlState.startsWith("08") || sqlState.startsWith("53") || sqlState.startsWith("57P")) {
                return "DB_UNAVAILABLE";
            }
            if (sqlState.startsWith("23")) {
                return "CONSTRAINT";
            }
        }
        return "UNKNOWN";
    }

    private static String sqlStateOf(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static boolean sqlState(Throwable e, String state) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
