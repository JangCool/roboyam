package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.QCommerceBankTransferEntity;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 계좌 송금({@code commerce_bank_transfers} — 결정 6·39·67, 9-3 과 같은 방식). 계좌 암호문은 송금 완료 때 지운다.
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). Native SQL 로 남긴 것과 이유: 송금 대기 만들기({@code ON CONFLICT (source_kind, source_id) DO NOTHING} —
 * 같은 원천은 한 번, 이미 있으면 0 행).
 */
@Repository
public class BankTransferStore extends GroupBuyStoreSupport {

    private static final QCommerceBankTransferEntity B = QCommerceBankTransferEntity.commerceBankTransferEntity;

    public record TransferRow(long id, String sourceKind, long sourceId, Long participationId, long userId, long amount, String status, String bankEnc, String accountNoEnc,
                              String holderEnc) {
    }

    private static final ConstructorExpression<TransferRow> ROW = Projections.constructor(TransferRow.class, B.id, B.sourceKind, B.sourceId, B.participationId, B.userId,
            B.amount, B.status, B.bankEnc, B.accountNoEnc, B.holderEnc);

    public BankTransferStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 송금 대기를 만든다(같은 원천은 한 번 — 이미 있으면 false) */
    public boolean insert(long id, String sourceKind, long sourceId, Long participationId, long userId, long amount, String reason, Long operatorId, Instant now) {
        return jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, participation_id, user_id, amount, status, reason, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'AWAITING_ACCOUNT', ?, ?, ?) ON CONFLICT (source_kind, source_id) DO NOTHING", id, sourceKind, sourceId, participationId, userId, amount,
                reason, operatorId, Timestamp.from(now)) == 1;
    }

    public Optional<TransferRow> find(long id) {
        return Optional.ofNullable(query.select(ROW).from(B).where(B.id.eq(id)).fetchFirst());
    }

    public Optional<TransferRow> bySource(String sourceKind, long sourceId) {
        return Optional.ofNullable(query.select(ROW).from(B).where(B.sourceKind.eq(sourceKind), B.sourceId.eq(sourceId)).fetchFirst());
    }

    public List<TransferRow> ofParticipation(long participationId) {
        return query.select(ROW).from(B).where(B.participationId.eq(participationId)).orderBy(B.id.asc()).fetch();
    }

    /** 계좌를 받는다(송금 전일 때만) */
    public boolean setAccount(long id, String bankEnc, String accountNoEnc, String holderEnc) {
        return query.update(B).set(B.bankEnc, bankEnc).set(B.accountNoEnc, accountNoEnc).set(B.holderEnc, holderEnc).set(B.status, "READY")
                .where(B.id.eq(id), B.status.in("AWAITING_ACCOUNT", "READY")).execute() == 1;
    }

    /** 송금 완료 — 계좌 암호문을 지운다(이긴 쪽만) */
    public boolean markSent(long id, long operatorId, Instant now) {
        return query.update(B).set(B.status, "SENT").set(B.sentBy, operatorId).set(B.sentAt, now).setNull(B.bankEnc).setNull(B.accountNoEnc).setNull(B.holderEnc)
                .set(B.accountErasedAt, now).where(B.id.eq(id), B.status.eq("READY")).execute() == 1;
    }
}
