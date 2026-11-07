package com.community.docintake.repository.master;

import com.community.docintake.entity.BizDocIntakeEntity;
import com.community.docintake.entity.DocIntakeChannel;
import com.community.docintake.entity.DocIntakeDoc;
import com.community.docintake.entity.DocIntakeHandling;
import com.community.docintake.entity.QBizDocIntakeEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 사업자 서류 종이 접수 기록(V140 {@code biz_doc_intakes}) — 줄을 지우지 않는다(사업자 삭제만 {@code BusinessStore#deleteProfile} 이 그 신원 줄을 지운다). 쓰기는 부르는 쪽이 대상(신청 건·사업자 신원) 줄을 잠근
 * 같은 트랜잭션에서 하고, 처리·폐기·잘못 기록은 「아직 열린 줄(잘못 기록 아님 · 폐기 안 함)」일 때만 바꾼다(아니면 false — 409). QueryDSL 만 쓴다.
 */
@Repository
public class DocIntakeStore extends RoboyamRepositoryQDSL {

    private static final QBizDocIntakeEntity I = QBizDocIntakeEntity.bizDocIntakeEntity;
    private static final QOperatorEntity RECEIVER = new QOperatorEntity("receiver");
    private static final QOperatorEntity DISPOSER = new QOperatorEntity("disposer");
    private static final QOperatorEntity VOIDER = new QOperatorEntity("voider");

    /** 접수 한 줄 — 운영자 이름 포함(지워진 운영자면 null) */
    public record IntakeRow(long id, DocIntakeDoc doc, Long applicationId, Long profileId, DocIntakeChannel channel, LocalDate receivedOn, Integer pages, String memo,
                            DocIntakeHandling handling, String storageLocation, Long receivedBy, String receivedByName, Instant recordedAt, LocalDate disposedOn,
                            Instant disposedAt, Long disposedBy, String disposedByName, String disposeReason, Long scanContractFileId, Long scanDocumentId,
                            Instant voidedAt, Long voidedBy, String voidedByName, String voidReason) {

        /** 처리를 바꾸거나 폐기·잘못 기록할 수 있는가 */
        public boolean open() {
            return voidedAt == null && handling.open();
        }
    }

    /** 새 접수 한 줄 */
    public record NewIntake(long id, DocIntakeDoc doc, long ownerId, DocIntakeChannel channel, LocalDate receivedOn, Integer pages, String memo, DocIntakeHandling handling,
                            String storageLocation, long operatorId, Instant now) {
    }

    private final EntityManager entityManager;

    public DocIntakeStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    public void insert(NewIntake n) {
        JpaStoreWrites.insert(entityManager, BizDocIntakeEntity.create(n.id(), n.doc(), n.ownerId(), n.channel(), n.receivedOn(), n.pages(), n.memo(), n.handling(),
                n.storageLocation(), n.operatorId(), n.now()));
    }

    /** 그 대상의 접수 줄 — 최근 기록 먼저 */
    public List<IntakeRow> list(DocIntakeDoc doc, long ownerId) {
        return select().where(I.doc.eq(doc), owner(doc, ownerId)).orderBy(I.recordedAt.desc(), I.id.desc()).fetch().stream().map(DocIntakeStore::row).toList();
    }

    /** 그 대상의 접수 한 줄(다른 대상의 줄이면 비어 있다) */
    public Optional<IntakeRow> find(DocIntakeDoc doc, long ownerId, long id) {
        return Optional.ofNullable(select().where(I.id.eq(id), I.doc.eq(doc), owner(doc, ownerId)).fetchFirst()).map(DocIntakeStore::row);
    }

    /** 처리 바꾸기(계속 보관 ↔ 폐기 예정) — 열린 줄만 */
    public boolean changeHandling(long id, DocIntakeHandling handling, String storageLocation, Instant now) {
        return query.update(I).set(I.handling, handling).set(I.storageLocation, storageLocation).set(I.updatedAt, now).where(I.id.eq(id), open()).execute() == 1;
    }

    /** 폐기 — 열린 줄만. 스캔본은 서류에 맞는 칸 하나(등록증은 없어도 된다) */
    public boolean dispose(long id, LocalDate disposedOn, String reason, Long scanContractFileId, Long scanDocumentId, long operatorId, Instant now) {
        return query.update(I).set(I.handling, DocIntakeHandling.DISPOSED).setNull(I.storageLocation).set(I.disposedOn, disposedOn).set(I.disposedAt, now)
                .set(I.disposedBy, operatorId).set(I.disposeReason, reason).set(I.scanContractFileId, scanContractFileId).set(I.scanDocumentId, scanDocumentId)
                .set(I.updatedAt, now).where(I.id.eq(id), open()).execute() == 1;
    }

    /** 잘못 기록 — 열린 줄만(폐기한 줄은 근거라 그을 수 없다) */
    public boolean markVoid(long id, String reason, long operatorId, Instant now) {
        return query.update(I).set(I.voidedAt, now).set(I.voidedBy, operatorId).set(I.voidReason, reason).set(I.updatedAt, now).where(I.id.eq(id), open())
                .execute() == 1;
    }

    /** 신청 건의 계약서 종이 근거 수 — 잘못 기록이 아닌 줄(폐기한 줄 포함) */
    public int contractEvidence(long applicationId) {
        Long n = query.select(I.count()).from(I).where(I.doc.eq(DocIntakeDoc.CONTRACT), I.applicationId.eq(applicationId), I.voidedAt.isNull()).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /** 사업자 신원의 등록증 종이 접수 중 잘못 기록이 아니고 그 시각 이후에 기록한 줄이 있는가(이번 신청 이후 받은 것) */
    public boolean hasCertificateSince(long profileId, Instant since) {
        return query.selectOne().from(I).where(I.doc.eq(DocIntakeDoc.CERTIFICATE), I.profileId.eq(profileId), I.voidedAt.isNull(), I.recordedAt.goe(since))
                .fetchFirst() != null;
    }

    /** 아직 열린 줄(잘못 기록 아님 · 폐기 안 함) */
    private static BooleanExpression open() {
        return I.voidedAt.isNull().and(I.handling.ne(DocIntakeHandling.DISPOSED));
    }

    private static BooleanExpression owner(DocIntakeDoc doc, long ownerId) {
        return doc == DocIntakeDoc.CONTRACT ? I.applicationId.eq(ownerId) : I.profileId.eq(ownerId);
    }

    private JPAQuery<Tuple> select() {
        return query.select(I.id, I.doc, I.applicationId, I.profileId, I.channel, I.receivedOn, I.pages, I.memo, I.handling, I.storageLocation, I.receivedBy,
                        RECEIVER.operatorName, I.recordedAt, I.disposedOn, I.disposedAt, I.disposedBy, DISPOSER.operatorName, I.disposeReason, I.scanContractFileId,
                        I.scanDocumentId, I.voidedAt, I.voidedBy, VOIDER.operatorName, I.voidReason).from(I)
                .leftJoin(RECEIVER).on(RECEIVER.id.eq(I.receivedBy)).leftJoin(DISPOSER).on(DISPOSER.id.eq(I.disposedBy)).leftJoin(VOIDER).on(VOIDER.id.eq(I.voidedBy));
    }

    private static IntakeRow row(Tuple t) {
        return new IntakeRow(t.get(I.id), t.get(I.doc), t.get(I.applicationId), t.get(I.profileId), t.get(I.channel), t.get(I.receivedOn), t.get(I.pages),
                t.get(I.memo), t.get(I.handling), t.get(I.storageLocation), t.get(I.receivedBy), t.get(RECEIVER.operatorName), t.get(I.recordedAt),
                t.get(I.disposedOn), t.get(I.disposedAt), t.get(I.disposedBy), t.get(DISPOSER.operatorName), t.get(I.disposeReason), t.get(I.scanContractFileId),
                t.get(I.scanDocumentId), t.get(I.voidedAt), t.get(I.voidedBy), t.get(VOIDER.operatorName), t.get(I.voidReason));
    }
}
