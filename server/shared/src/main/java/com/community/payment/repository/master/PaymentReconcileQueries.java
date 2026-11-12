package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.O;
import static com.community.payment.repository.master.PaymentOrderStore.ts;

import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 대사가 우리 쪽에서 읽는 것 — 처음 결제가 생긴 순간, 그 날 결제되거나 취소가 끝난 우리 결제(포트원 목록에 없던 우리 쪽 변화를 단건 조회로 확인한다).
 *
 * <p>QueryDSL 이 기본이다. Native SQL 로 남긴 것과 이유: {@link #localChanges} — 결제 주문·취소 대기열 두 표의 {@code UNION}(겹침 없애기 + DB 차례). JPQL·QueryDSL 에
 * {@code UNION} 이 없고, {@code OR EXISTS} 한 질의로 바꾸면 두 색인({@code ix_payment_orders_paid_at}·{@code ix_payment_cancels_completed})을 각각 타지 못해 결제 주문 표를 다 훑는다.
 */
@Repository
public class PaymentReconcileQueries extends IrobitRepositoryQDSL {

    private final EntityManager entityManager;

    public PaymentReconcileQueries(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 가장 오래된 결제 주문을 만든 순간(없으면 빈 값) — 처음 대사할 때 「아직 안 한 날」의 시작 */
    public Optional<Instant> firstOrderAt() {
        return Optional.ofNullable(query.select(O.createdAt.min()).from(O).fetchOne());
    }

    /**
     * [from, until) 에 결제됐거나(결제된 계열) 성공한 취소가 끝난 우리 결제의 결제 ID(차례 — 결제 ID).
     *
     * <p>Native SQL 인 이유: 클래스 설명({@code UNION}).
     */
    public List<String> localChanges(Instant from, Instant until) {
        @SuppressWarnings("unchecked")
        List<String> ids = entityManager.createNativeQuery("SELECT payment_id FROM payment_orders WHERE paid_at >= ?1 AND paid_at < ?2 "
                        + "AND status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED') "
                        + "UNION SELECT o.payment_id FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id "
                        + "WHERE c.status = 'SUCCEEDED' AND c.completed_at >= ?1 AND c.completed_at < ?2 ORDER BY 1")
                .setParameter(1, ts(from)).setParameter(2, ts(until)).getResultList();
        return ids;
    }
}
