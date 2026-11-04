package com.community.analytics.collect;

import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.AnalyticsWriteStore;
import com.community.analytics.store.AnalyticsWriteStore.DailyRow;
import com.community.analytics.store.AnalyticsWriteStore.RawRow;
import com.community.analytics.store.AnalyticsWriteStore.VisitorRow;
import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   <li>유입처 일별 더하기 (1문장)</li>
 *   <li>이동 일별 더하기 (1문장)</li>
 * </ol>
 * 서버 여러 대의 정확성 근거는 {@link AnalyticsWriteStore} 머리말. 묶음 하나가 실패하면(트랜잭션째 되돌려졌다) 그 묶음만 메모리에 되돌려 다음 1분에 다시 쓴다(상한은 버퍼가 지킨다).
 * 서버가 갑자기 꺼지면 아직 쓰지 않은 최대 1분치를 잃는다(통계라 허용 — 계약). 「오늘 셌다」 표시는 DB 에 있어 재시작해도 같은 방문자를 다시 세지 않는다.
 */
@Component
public class VisitFlusher {

    private static final Logger log = LoggerFactory.getLogger(VisitFlusher.class);

    /** PostgreSQL 「행에 맞는 조각이 없다」(check_violation) */
    private static final String NO_PARTITION = "23514";

    private final VisitCollector collector;
    private final AnalyticsWriteStore store;
    private final AnalyticsDatabase db;

    public VisitFlusher(VisitCollector collector, AnalyticsWriteStore store, AnalyticsDatabase db) {
        this.collector = collector;
        this.store = store;
        this.db = db;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public synchronized void flush() {
        VisitBuffer buffer = collector.buffer();
        VisitBuffer.Batch b = buffer.drain();
        if (b.isEmpty()) {
            return;
        }
        if (!b.visitors().isEmpty() || !b.pageViews().isEmpty() || !b.active().isEmpty()) {
            List<VisitorRow> visitors = new ArrayList<>(b.visitors().size());
            b.visitors().forEach((k, v) -> visitors.add(new VisitorRow(k.day(), k.visitorHash(), v.hour(), v.newVisitor())));
            if (!run("방문자", () -> store.visits(visitors, b.pageViews(), b.active()))) {
                buffer.restoreVisits(b);
            }
        }
        if (!b.raw().isEmpty() || !b.pages().isEmpty()) {
            List<RawRow> raw = new ArrayList<>(b.raw().size());
            b.raw().forEach(r -> raw.add(new RawRow(r.id(), r.viewedAt(), r.visitorHash(), r.sessionHash(), r.path(), r.kind(), r.prevPath(), r.referrerDomain(),
                    r.referrerGroup(), r.utmSource(), r.utmMedium(), r.utmCampaign(), r.device(), r.newVisitor())));
            List<DailyRow> pages = new ArrayList<>(b.pages().size());
            b.pages().forEach((k, n) -> pages.add(new DailyRow(k.day(), k.path(), k.kind(), n)));
            Runnable write = () -> {
                store.raw(raw);
                store.pages(pages);
            };
            boolean ok = run("원본", write);
            if (!ok && lastFailureWasNoPartition) {
                ok = run("원본 조각 만들기", store::ensurePartitions) && run("원본", write);
            }
            if (!ok) {
                buffer.restoreRaw(b);
            }
        }
        if (!b.referrers().isEmpty()) {
            List<DailyRow> rows = new ArrayList<>(b.referrers().size());
            b.referrers().forEach((k, n) -> rows.add(new DailyRow(k.day(), k.group(), k.domain(), n)));
            if (!run("유입처", () -> store.referrers(rows))) {
                buffer.restoreReferrers(b);
            }
        }
        if (!b.flows().isEmpty()) {
            List<DailyRow> rows = new ArrayList<>(b.flows().size());
            b.flows().forEach((k, n) -> rows.add(new DailyRow(k.day(), k.from(), k.to(), n)));
            if (!run("이동", () -> store.flows(rows))) {
                buffer.restoreFlows(b);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        flush();
    }

    /** 마지막 실패가 「조각 없음」이었나 — flush 는 synchronized 라 한 번에 하나만 돈다 */
    private boolean lastFailureWasNoPartition;

    /** 분석 연결의 트랜잭션 하나로 — 실패하면 경고만 남기고 false(방문 신호 받기에는 영향이 없다) */
    private boolean run(String what, Runnable work) {
        lastFailureWasNoPartition = false;
        try {
            db.inTransaction(() -> {
                work.run();
                return null;
            });
            return true;
        } catch (RuntimeException e) {
            lastFailureWasNoPartition = sqlState(e, NO_PARTITION);
            log.warn("방문 {} 몰아쓰기에 실패해 다음 1분에 다시 쓴다: {}", what, e.getClass().getSimpleName());
            return false;
        }
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
