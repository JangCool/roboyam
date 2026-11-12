package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.ts;

import com.community.payment.entity.QPaymentReconcileDayEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

/**
 * 대사한 날({@code payment_reconcile_days}, KST 날짜) — 「마지막으로 대사한 날 다음부터 어제까지」(commerce.md 15-3 규칙 ②)의 기록. 줄이 없는 날은 「아직 안 함」이다.
 * 상태: RUNNING(도는 중) · DONE(불일치 없음) · MISMATCH(불일치 있음) · FAILED(포트원 장애 등 — 다음 실행이 다시 한다). 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>읽기는 QueryDSL. Native SQL 로 남긴 것과 이유: 쓰기({@code INSERT … ON CONFLICT (day) DO UPDATE} — 같은 날을 다시 대사하면 줄을 덮어쓰는 upsert, JPQL·QueryDSL 에 대응 문법이 없다),
 * 아직 안 한 날 찾기({@code generate_series} — 줄이 없는 날까지 날짜 목록을 DB 가 만든다).
 */
@Repository
public class PaymentReconcileDayStore extends IrobitRepositoryQDSL {

    /** 날짜 상태(DB CHECK 와 같은 목록 — 「아직 안 함」은 줄이 없다) */
    public enum Status {
        RUNNING, DONE, MISMATCH, FAILED
    }

    /** 날짜 한 줄 */
    public record DayRow(LocalDate day, Status status, int checkedCount, int issueCount, Long runId, Instant finishedAt) {
    }

    private static final QPaymentReconcileDayEntity D = QPaymentReconcileDayEntity.paymentReconcileDayEntity;

    private final EntityManager entityManager;

    public PaymentReconcileDayStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /**
     * 그 날을 도는 중으로 둔다(건수는 이전 값 그대로).
     *
     * <p>Native SQL 인 이유: 클래스 설명({@code ON CONFLICT (day) DO UPDATE}).
     */
    public void markRunning(LocalDate day, Long runId) {
        entityManager.createNativeQuery("INSERT INTO payment_reconcile_days (day, status, run_id) VALUES (?1, 'RUNNING', ?2) "
                        + "ON CONFLICT (day) DO UPDATE SET status = 'RUNNING', run_id = excluded.run_id")
                .unwrap(NativeQuery.class)
                .setParameter(1, day, LocalDate.class).setParameter(2, runId, Long.class).executeUpdate();
    }

    /**
     * 그 날의 결과를 남긴다.
     *
     * <p>Native SQL 인 이유: 클래스 설명({@code ON CONFLICT (day) DO UPDATE}).
     */
    public void finish(LocalDate day, Status status, int checkedCount, int issueCount, Long runId, Instant now) {
        entityManager.createNativeQuery("INSERT INTO payment_reconcile_days (day, status, checked_count, issue_count, run_id, finished_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6) "
                        + "ON CONFLICT (day) DO UPDATE SET status = excluded.status, checked_count = excluded.checked_count, issue_count = excluded.issue_count, "
                        + "run_id = excluded.run_id, finished_at = excluded.finished_at")
                .unwrap(NativeQuery.class)
                .setParameter(1, day, LocalDate.class).setParameter(2, status.name()).setParameter(3, checkedCount).setParameter(4, issueCount)
                .setParameter(5, runId, Long.class).setParameter(6, ts(now)).executeUpdate();
    }

    public Optional<DayRow> find(LocalDate day) {
        return Optional.ofNullable(rows().where(D.reconcileDay.eq(day)).fetchOne()).map(PaymentReconcileDayStore::row);
    }

    /** [from, to] 의 기록이 있는 날(날짜순) */
    public List<DayRow> range(LocalDate from, LocalDate to) {
        return rows().where(D.reconcileDay.between(from, to)).orderBy(D.reconcileDay.asc()).fetch().stream().map(PaymentReconcileDayStore::row).toList();
    }

    /** 대사가 끝난(DONE·MISMATCH) 마지막 날 */
    public Optional<LocalDate> lastChecked() {
        return Optional.ofNullable(query.select(D.reconcileDay.max()).from(D).where(D.status.in(Status.DONE.name(), Status.MISMATCH.name())).fetchOne());
    }

    /**
     * [from, to] 중 아직 끝나지 않은 날(줄이 없거나 RUNNING·FAILED) — 날짜순, 최대 {@code limit} 개, {@code after} 다음부터(이어 받기 체크포인트).
     * 하루를 놓쳐도·실패해도 다음 실행이 메꾼다.
     *
     * <p>Native SQL 인 이유: 클래스 설명({@code generate_series}).
     */
    public List<LocalDate> unfinished(LocalDate from, LocalDate to, LocalDate after, int limit) {
        LocalDate start = after != null && !after.isBefore(from) ? after.plusDays(1) : from;
        if (start.isAfter(to)) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<LocalDate> days = entityManager.createNativeQuery("SELECT CAST(d AS date) AS day FROM generate_series(CAST(?1 AS date), CAST(?2 AS date), interval '1 day') d "
                        + "WHERE NOT EXISTS (SELECT 1 FROM payment_reconcile_days r WHERE r.day = CAST(d AS date) AND r.status IN ('DONE', 'MISMATCH')) ORDER BY d LIMIT ?3")
                .unwrap(NativeQuery.class)
                .addScalar("day", LocalDate.class)
                .setParameter(1, start, LocalDate.class).setParameter(2, to, LocalDate.class).setParameter(3, limit).getResultList();
        return days;
    }

    private JPAQuery<Tuple> rows() {
        return query.select(D.reconcileDay, D.status, D.checkedCount, D.issueCount, D.runId, D.finishedAt).from(D);
    }

    private static DayRow row(Tuple t) {
        return new DayRow(t.get(D.reconcileDay), Status.valueOf(t.get(D.status)), t.get(D.checkedCount), t.get(D.issueCount), t.get(D.runId), t.get(D.finishedAt));
    }
}
