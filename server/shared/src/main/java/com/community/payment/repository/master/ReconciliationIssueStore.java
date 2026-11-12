package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.ts;

import com.community.payment.entity.QReconciliationIssueEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대사 이슈({@code reconciliation_issues}) 쓰기 — 콘솔 직접 취소 웹훅(P18, 2a-2)·대사 배치({@code ReconciliationService}, 2a-3)가 열고 운영자가 처리로 닫는다(2a-3).
 * 열린 이슈는 (종류, 결제) 하나 — 같은 것이 다시 와도 새 줄을 만들지 않는다. 자동 보정하지 않는다(commerce.md 16-1). 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>닫기는 QueryDSL 조건부 UPDATE. Native SQL 로 남긴 것과 이유: 열기({@code INSERT … ON CONFLICT (type, payment_id) WHERE status = 'OPEN' DO NOTHING} —
 * 부분 유일 색인의 멱등 넣기, JPQL·QueryDSL 에 대응 문법이 없다).
 */
@Repository
public class ReconciliationIssueStore extends IrobitRepositoryQDSL {

    /** 이슈 종류(DB CHECK 와 같은 목록) */
    public enum Type {
        PG_PAID_LOCAL_UNPAID, LOCAL_PAID_PG_MISSING, AMOUNT_MISMATCH, CANCEL_MISMATCH, PG_ONLY
    }

    static final QReconciliationIssueEntity I = QReconciliationIssueEntity.reconciliationIssueEntity;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final EntityManager entityManager;

    public ReconciliationIssueStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /**
     * 열린 이슈를 남긴다 — 같은 (종류, 결제)의 열린 이슈가 있으면 넣지 않는다.
     *
     * <p>Native SQL 인 이유: 클래스 설명({@code ON CONFLICT … WHERE status = 'OPEN' DO NOTHING}).
     *
     * @return 넣었으면 true
     */
    public boolean open(long id, Type type, String paymentId, Long paymentOrderId, LocalDate detectedDay, Map<String, Object> detail, Instant now) {
        return entityManager.createNativeQuery("INSERT INTO reconciliation_issues (id, type, payment_id, payment_order_id, detected_day, detail, status, created_at) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5, CAST(?6 AS jsonb), 'OPEN', ?7) ON CONFLICT (type, payment_id) WHERE status = 'OPEN' DO NOTHING")
                .unwrap(NativeQuery.class)
                .setParameter(1, id).setParameter(2, type.name()).setParameter(3, paymentId).setParameter(4, paymentOrderId, Long.class)
                .setParameter(5, detectedDay, LocalDate.class).setParameter(6, detail == null || detail.isEmpty() ? null : JSON.writeValueAsString(detail), String.class)
                .setParameter(7, ts(now)).executeUpdate() == 1;
    }

    /**
     * 열린 이슈를 처리로 닫는다(운영자 — 처리자·사유). 열려 있을 때만.
     *
     * @return 닫았으면 true
     */
    public boolean resolve(long id, long operatorId, String note, Instant now) {
        return query.update(I).set(I.status, "RESOLVED").set(I.resolveNote, note).set(I.resolvedBy, operatorId).set(I.resolvedAt, now)
                .where(I.id.eq(id), I.status.eq("OPEN")).execute() == 1;
    }
}
