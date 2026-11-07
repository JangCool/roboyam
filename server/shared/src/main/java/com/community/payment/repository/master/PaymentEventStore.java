package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.ts;

import com.community.payment.entity.PaymentEventEntity;
import com.community.payment.entity.QPaymentEventEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 이력({@code payment_events}) — 결제 한 건의 일(만듦·결제·이상·만료·취소·웹훅)을 남긴다. 거래 기록이라 5년 보관(commerce.md 17-3), {@code detail} 에 개인정보를 넣지 않는다.
 * 웹훅은 {@code provider_event_id}(webhook-id) 부분 유일로 한 번만 남는다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이 기본이다 — 넣기는 엔티티를 넣고 바로 내보낸다({@code persistAndFlush}: 결제 확정 트랜잭션이 처리기를 JDBC 세이브포인트로 감싸기 전에 미뤄 둔 쓰기가 없게).
 * Native SQL 로 남긴 것과 이유: 처리한 웹훅 넣기({@code INSERT … ON CONFLICT (provider_event_id) WHERE provider_event_id IS NOT NULL DO NOTHING} — 부분 유일 색인의 멱등 넣기,
 * JPQL·QueryDSL 에 대응 문법이 없다).
 */
@Repository
public class PaymentEventStore extends RoboyamRepositoryQDSL {

    /** 이력 종류(DB CHECK 와 같은 목록 — RECHECKED 는 V98, 운영자의 「포트원에서 다시 확인」) */
    public enum Kind {
        CREATED, PAID, ANOMALY, EXPIRED, CANCEL_REQUESTED, CANCEL_SUCCEEDED, CANCEL_RETRY, CANCEL_GAVE_UP, CANCEL_MANUAL, EXTERNAL_CANCEL, WEBHOOK, RECHECKED
    }

    static final QPaymentEventEntity E = QPaymentEventEntity.paymentEventEntity;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PaymentEventRepository repository;
    private final EntityManager entityManager;

    public PaymentEventStore(JPAQueryFactory query, PaymentEventRepository repository, EntityManager entityManager) {
        super(query);
        this.repository = repository;
        this.entityManager = entityManager;
    }

    /** 이력 한 줄 — 넣고 바로 내보낸다 */
    public void insert(long id, long paymentOrderId, Kind kind, Map<String, Object> detail, Instant now) {
        repository.persistAndFlush(PaymentEventEntity.of(id, paymentOrderId, kind.name(), json(detail), now));
    }

    /**
     * 처리한 웹훅을 남긴다 — 같은 webhook-id 가 이미 있으면 넣지 않는다. 트랜잭션 안에서 부른다.
     *
     * <p>Native SQL 인 이유: 부분 유일 색인({@code ux_payment_events_provider_event})의 {@code ON CONFLICT … DO NOTHING} 은 JPQL·QueryDSL 에 대응 문법이 없다
     * (「찾아보고 없으면 넣기」는 같은 웹훅이 동시에 두 번 오면 깨진다).
     *
     * @return 넣었으면 true
     */
    public boolean insertWebhook(long id, long paymentOrderId, String webhookId, Map<String, Object> detail, Instant now) {
        return entityManager.createNativeQuery("INSERT INTO payment_events (id, payment_order_id, kind, provider_event_id, detail, created_at) "
                        + "VALUES (?1, ?2, 'WEBHOOK', ?3, CAST(?4 AS jsonb), ?5) ON CONFLICT (provider_event_id) WHERE provider_event_id IS NOT NULL DO NOTHING")
                .unwrap(NativeQuery.class)
                .setParameter(1, id).setParameter(2, paymentOrderId).setParameter(3, webhookId).setParameter(4, json(detail), String.class).setParameter(5, ts(now))
                .executeUpdate() == 1;
    }

    /** 그 webhook-id 를 이미 처리했는가 */
    public boolean webhookProcessed(String webhookId) {
        return query.selectOne().from(E).where(E.providerEventId.eq(webhookId)).fetchFirst() != null;
    }

    /** 그 포트원 취소 내역을 「우리 것이 아닌 취소」로 이미 남겼는가(같은 콘솔 취소가 다음 웹훅에도 실려 온다) — {@code detail ->> 'pgCancelId'} 와 같은 함수 */
    public boolean externalCancelRecorded(long paymentOrderId, String pgCancelId) {
        return query.selectOne().from(E)
                .where(E.paymentOrderId.eq(paymentOrderId), E.kind.eq(Kind.EXTERNAL_CANCEL.name()),
                        Expressions.stringTemplate("function('jsonb_extract_path_text', {0}, 'pgCancelId')", E.detail).eq(pgCancelId))
                .fetchFirst() != null;
    }

    private static String json(Map<String, Object> detail) {
        return detail == null || detail.isEmpty() ? null : JSON.writeValueAsString(detail);
    }
}
