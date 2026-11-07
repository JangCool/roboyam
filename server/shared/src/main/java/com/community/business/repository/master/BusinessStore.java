package com.community.business.repository.master;

import com.community.ad.entity.QAdAdvertiserEntity;
import com.community.ad.entity.QAdAdvertiserViewEntity;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.business.entity.BusinessDocumentEntity;
import com.community.business.entity.BusinessHistoryEntity;
import com.community.business.entity.BusinessNtsCheckEntity;
import com.community.business.entity.BusinessSensitiveViewEntity;
import com.community.business.entity.NtsBizStatus;
import com.community.business.entity.NtsValidity;
import com.community.business.entity.QBusinessDocumentEntity;
import com.community.business.entity.QBusinessHistoryEntity;
import com.community.business.entity.QBusinessNtsCheckEntity;
import com.community.business.entity.QBusinessProfileEntity;
import com.community.business.entity.QBusinessSensitiveViewEntity;
import com.community.business.entity.QBusinessServiceEntity;
import com.community.docintake.entity.DocFileChannel;
import com.community.docintake.entity.DocIntakeDoc;
import com.community.docintake.entity.QBizDocIntakeEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 사업자 신원 곁의 표 — 등록증 사본(내용이 커서 정보 조회는 프로젝션으로 내용을 뺀다) · 국세청 조회 스냅샷 · 열람 기록 · 처리 기록(V111).
 * 줄은 고치지 않고 쌓는다(등록증의 붙이기·파기만 고친다). QueryDSL 만 쓴다 — 넣기는 {@code persist} + {@code flush}({@link JpaStoreWrites}).
 */
@Repository
public class BusinessStore extends RoboyamRepositoryQDSL {

    // 처리 기록 행위(ck_bh_action)
    public static final String APPLIED = "APPLIED";
    public static final String UPDATED = "UPDATED";
    public static final String NTS_CHECKED = "NTS_CHECKED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String UNSUSPENDED = "UNSUSPENDED";
    public static final String REVEALED = "REVEALED";
    public static final String CERT_VIEWED = "CERT_VIEWED";
    public static final String WITHDRAWN = "WITHDRAWN";
    /** 서비스만 정지·해제(V130 — 처리 기록에 서비스가 함께 남는다) */
    public static final String SERVICE_SUSPENDED = "SERVICE_SUSPENDED";
    public static final String SERVICE_RESUMED = "SERVICE_RESUMED";
    /** 등록증 종이 접수 기록(V140 — biz-doc-intake-contract): 접수 · 처리 바꾸기 · 폐기 · 잘못 기록 */
    public static final String PAPER_RECEIVED = "PAPER_RECEIVED";
    public static final String PAPER_HANDLING = "PAPER_HANDLING";
    public static final String PAPER_DISPOSED = "PAPER_DISPOSED";
    public static final String PAPER_VOIDED = "PAPER_VOIDED";
    /** 운영자가 등록증 사본을 올렸다(전자 팩스·스캔 — V140) */
    public static final String CERT_UPLOADED = "CERT_UPLOADED";

    // 기록한 사람 종류(ck_bh_actor)
    public static final String MEMBER = "MEMBER";
    public static final String OPERATOR = "OPERATOR";
    public static final String SYSTEM = "SYSTEM";

    private static final QBusinessDocumentEntity DOC = QBusinessDocumentEntity.businessDocumentEntity;
    private static final QBusinessNtsCheckEntity NTS = QBusinessNtsCheckEntity.businessNtsCheckEntity;
    private static final QBusinessSensitiveViewEntity VIEW = QBusinessSensitiveViewEntity.businessSensitiveViewEntity;
    private static final QBusinessHistoryEntity H = QBusinessHistoryEntity.businessHistoryEntity;
    private static final QBusinessProfileEntity BP = QBusinessProfileEntity.businessProfileEntity;
    private static final QBusinessServiceEntity BS = QBusinessServiceEntity.businessServiceEntity;
    private static final QBizApplicationEntity APP = QBizApplicationEntity.bizApplicationEntity;
    private static final QBizDocIntakeEntity INTAKE = QBizDocIntakeEntity.bizDocIntakeEntity;
    private static final QAdAdvertiserEntity ADV = QAdAdvertiserEntity.adAdvertiserEntity;
    private static final QAdAdvertiserViewEntity ADV_VIEW = QAdAdvertiserViewEntity.adAdvertiserViewEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QProfileEntity P = QProfileEntity.profileEntity;

    /** 등록증 사본 한 장의 정보(내용 없음) */
    public record DocumentRow(long id, long userId, Long profileId, String fileName, String contentType, int sizeBytes, Instant createdAt, Instant attachedAt,
                              Instant erasedAt) {
    }

    /** 국세청 조회 한 줄 — 상태 + 진위(V125, 그 전 줄은 UNCHECKED) */
    public record NtsRow(long id, long profileId, NtsBizStatus status, String taxType, LocalDate closedOn, String message, Instant checkedAt, Long checkedBy,
                         NtsValidity validity) {
    }

    /** 신원에 붙은 등록증 사본 한 장(내용 없음) — 들어온 길·올린 운영자(회원이 올렸으면 null, V140) */
    public record ProfileFileRow(long id, String fileName, String contentType, int sizeBytes, Instant createdAt, DocFileChannel channel, Long uploadedBy,
                                 String uploadedByName) {
    }

    /** 처리 기록 한 줄 — 이름은 운영자 이름 또는 회원 닉네임(지워졌으면 null) */
    public record HistoryRow(Instant at, String action, String actorType, Long actorId, String actorName, String note, String service) {
    }

    /** 마지막 등록증 열람 */
    public record LastView(String operatorName, Instant at) {
    }

    private static final Expression<NtsRow> NTS_ROW = Projections.constructor(NtsRow.class, NTS.id, NTS.profileId, NTS.status, NTS.taxType, NTS.closedOn,
            NTS.message, NTS.checkedAt, NTS.checkedBy, NTS.validity);

    private final EntityManager entityManager;

    public BusinessStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    // ── 등록증 사본 ─────────────────────────────────────────────────────────

    public void insertDocument(long id, long userId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256, Instant now) {
        JpaStoreWrites.insert(entityManager, BusinessDocumentEntity.byMember(id, userId, fileName, contentType, sizeBytes, encrypted, sha256, now));
    }

    public Optional<DocumentRow> document(long id) {
        return Optional.ofNullable(query.select(Projections.constructor(DocumentRow.class, DOC.id, DOC.userId, DOC.profileId, DOC.fileName, DOC.contentType,
                DOC.sizeBytes, DOC.createdAt, DOC.attachedAt, DOC.erasedAt)).from(DOC).where(DOC.id.eq(id)).fetchFirst());
    }

    /** 신청·수정에 붙인다(한 번 붙은 파일은 다른 신원에 붙지 않는다) */
    public boolean attachDocument(long id, long profileId, Instant now) {
        return query.update(DOC).set(DOC.profileId, profileId).set(DOC.attachedAt, DOC.attachedAt.coalesce(now))
                .where(DOC.id.eq(id), DOC.erasedAt.isNull(), DOC.profileId.isNull().or(DOC.profileId.eq(profileId))).execute() == 1;
    }

    /**
     * 운영자가 올린 등록증 사본(V140 — 전자 팩스·스캔본) — 주인은 그 신원의 회원, 올리면서 바로 그 신원에 붙는다. 지금 등록증으로 바꾸는 것은 부르는 쪽(신원 엔티티)
     */
    public void insertOperatorDocument(long id, long userId, long profileId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256,
                                       DocFileChannel channel, long operatorId, Instant now) {
        JpaStoreWrites.insert(entityManager, BusinessDocumentEntity.byOperator(id, userId, profileId, fileName, contentType, sizeBytes, encrypted, sha256, channel,
                operatorId, now));
    }

    /** 신원에 붙은 등록증 사본 중 파기되지 않은 것(올린 순) */
    public List<ProfileFileRow> profileDocuments(long profileId) {
        return query.select(Projections.constructor(ProfileFileRow.class, DOC.id, DOC.fileName, DOC.contentType, DOC.sizeBytes, DOC.createdAt, DOC.channel,
                        DOC.uploadedBy, O.operatorName)).from(DOC).leftJoin(O).on(O.id.eq(DOC.uploadedBy))
                .where(DOC.profileId.eq(profileId), DOC.erasedAt.isNull()).orderBy(DOC.createdAt.asc(), DOC.id.asc()).fetch();
    }

    /** 그 시각 뒤 회원이 올린 사본 수(하루 한도 — 운영자가 올린 줄은 세지 않는다, V140) */
    public int uploadsSince(long userId, Instant since) {
        return count(query.select(DOC.count()).from(DOC).where(DOC.userId.eq(userId), DOC.uploadedBy.isNull(), DOC.createdAt.goe(since)).fetchOne());
    }

    /** 암호문 내용 — 파기됐으면 비어 있다 */
    public Optional<byte[]> documentContent(long id) {
        return Optional.ofNullable(query.select(DOC.content).from(DOC).where(DOC.id.eq(id), DOC.erasedAt.isNull()).fetchFirst());
    }

    // ── 국세청 조회 ─────────────────────────────────────────────────────────

    public void insertNtsCheck(long id, long profileId, NtsBizStatus status, NtsValidity validity, String taxType, LocalDate closedOn, String message, Instant checkedAt,
                               Long checkedBy) {
        JpaStoreWrites.insert(entityManager, BusinessNtsCheckEntity.create(id, profileId, status, validity, taxType, closedOn, message, checkedAt, checkedBy));
    }

    public Optional<NtsRow> latestNtsCheck(long profileId) {
        return Optional.ofNullable(query.select(NTS_ROW).from(NTS).where(NTS.profileId.eq(profileId)).orderBy(NTS.checkedAt.desc(), NTS.id.desc()).fetchFirst());
    }

    /** 신원마다 가장 최근 조회(목록 표) — 더 늦은 조회가 없는 줄(조회 시각, 같으면 큰 ID — 예전 {@code DISTINCT ON} 과 같은 판정) */
    public Map<Long, NtsRow> latestNtsChecks(Collection<Long> profileIds) {
        Map<Long, NtsRow> out = new HashMap<>();
        if (profileIds.isEmpty()) {
            return out;
        }
        QBusinessNtsCheckEntity later = new QBusinessNtsCheckEntity("later");
        query.select(NTS_ROW).from(NTS).where(NTS.profileId.in(profileIds), JPAExpressions.selectOne().from(later)
                        .where(later.profileId.eq(NTS.profileId), later.checkedAt.gt(NTS.checkedAt).or(later.checkedAt.eq(NTS.checkedAt).and(later.id.gt(NTS.id))))
                        .notExists())
                .fetch().forEach(row -> out.put(row.profileId(), row));
        return out;
    }

    // ── 열람 기록 ───────────────────────────────────────────────────────────

    public void insertView(long id, long profileId, String kind, long operatorId, String reason, String ip, Instant now) {
        JpaStoreWrites.insert(entityManager, BusinessSensitiveViewEntity.create(id, profileId, kind, operatorId, reason, ip, now));
    }

    public Optional<LastView> lastView(long profileId, String kind) {
        return Optional.ofNullable(query.select(Projections.constructor(LastView.class, O.operatorName, VIEW.createdAt)).from(VIEW).leftJoin(O).on(O.id.eq(VIEW.operatorId))
                .where(VIEW.profileId.eq(profileId), VIEW.kind.eq(kind)).orderBy(VIEW.createdAt.desc()).fetchFirst());
    }

    // ── 처리 기록 ───────────────────────────────────────────────────────────

    public void insertHistory(long id, long profileId, String action, String actorType, Long actorId, String note, Instant now) {
        insertHistory(id, profileId, action, actorType, actorId, note, null, now);
    }

    /** 서비스 하나에 대한 처리 기록(서비스만 정지·해제) */
    public void insertHistory(long id, long profileId, String action, String actorType, Long actorId, String note, String service, Instant now) {
        JpaStoreWrites.insert(entityManager, BusinessHistoryEntity.create(id, profileId, action, actorType, actorId, note == null ? null : cut(note, 300), service, now));
    }

    /** 처리 기록(최근 먼저, 최대 100) — 행위자 이름은 운영자 이름·회원 닉네임 */
    public List<HistoryRow> history(long profileId) {
        return query.select(H.createdAt, H.action, H.actorType, H.actorId, O.operatorName, P.nickname, H.note, H.service).from(H)
                .leftJoin(O).on(H.actorType.eq(OPERATOR), O.id.eq(H.actorId))
                .leftJoin(P).on(H.actorType.eq(MEMBER), P.userId.eq(H.actorId))
                .where(H.profileId.eq(profileId)).orderBy(H.createdAt.desc(), H.id.desc()).limit(100).fetch().stream()
                .map(t -> {
                    String type = t.get(H.actorType);
                    String name = OPERATOR.equals(type) ? t.get(O.operatorName) : MEMBER.equals(type) ? t.get(P.nickname) : null;
                    return new HistoryRow(t.get(H.createdAt), t.get(H.action), type, t.get(H.actorId), name, t.get(H.note), t.get(H.service));
                }).toList();
    }

    /** 그 시각 뒤 신청(재신청) 수 — 반려 뒤 재신청 하루 3회 */
    public int appliedSince(long profileId, Instant since) {
        return count(query.select(H.count()).from(H).where(H.profileId.eq(profileId), H.action.eq(APPLIED), H.createdAt.goe(since)).fetchOne());
    }

    // ── 삭제(business-contract 「사업자 삭제」) ────────────────────────────────

    /**
     * 그 신원에 광고·핫딜 신청 건이 한 줄이라도 있는가(상태 무관 — 취소·반려·완료 포함). 결제 주문(BIZ_APPLICATION)·입금·환불·처리 기록은 모두 신청 건에 매달려 있다
     * — 신청 건이 거래의 원천이다. 있으면 지우지 않는다(거래 기록 5년 보관).
     */
    public boolean hasDealings(long profileId) {
        return query.selectOne().from(APP).where(APP.businessProfileId.eq(profileId)).fetchFirst() != null;
    }

    /**
     * 신원 줄과 딸린 사업자 표의 줄을 모두 지운다(외래키 차례) — 신원이 가리키는 등록증 끊기 → 등록증 사본(암호문) · 국세청 스냅샷 · 서비스 줄 · 처리 기록 · 열람 기록 →
     * 연결된 광고주의 사업자 연결 끊기(광고주는 계정 없는 광고주로 남는다) → 신원 줄. 회원 계정·회원 알림·회원이 아직 붙이지 않은 사본은 건드리지 않는다.
     * 호출하는 쪽이 신원 줄을 잠그고(그 엔티티를 영속 문맥에서 떼고) 같은 트랜잭션에서 부른다.
     *
     * @return 지운 신원 줄 수(0 이면 그 사이 없어졌다)
     */
    public int deleteProfile(long profileId) {
        query.update(BP).setNull(BP.certificateId).where(BP.id.eq(profileId)).execute();
        // 등록증 종이 접수 기록(V140) — 등록증 사본보다 먼저(스캔본 외래키)
        query.delete(INTAKE).where(INTAKE.doc.eq(DocIntakeDoc.CERTIFICATE), INTAKE.profileId.eq(profileId)).execute();
        query.delete(DOC).where(DOC.profileId.eq(profileId)).execute();
        query.delete(NTS).where(NTS.profileId.eq(profileId)).execute();
        query.delete(BS).where(BS.profileId.eq(profileId)).execute();
        query.delete(H).where(H.profileId.eq(profileId)).execute();
        query.delete(VIEW).where(VIEW.profileId.eq(profileId)).execute();
        // 광고주 관리의 사업자 광고주 연락처 열람 기록(V131 — 키 = 신원)
        query.delete(ADV_VIEW).where(ADV_VIEW.businessProfileId.eq(profileId)).execute();
        query.update(ADV).setNull(ADV.businessProfileId).where(ADV.businessProfileId.eq(profileId)).execute();
        return (int) query.delete(BP).where(BP.id.eq(profileId)).execute();
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private static int count(Long n) {
        return n == null ? 0 : Math.toIntExact(n);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
