package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationEntity;
import com.community.bizapplication.entity.BizApplicationEventEntity;
import com.community.bizapplication.entity.BizApplicationItemEntity;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizCreativeVersionEntity;
import com.community.bizapplication.entity.BizDomain;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPauseChannel;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRejectCode;
import com.community.bizapplication.entity.BizSource;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizApplicationEventEntity;
import com.community.bizapplication.entity.QBizApplicationItemEntity;
import com.community.bizapplication.entity.QBizCreativeVersionEntity;
import com.community.bizapplication.entity.QBizDepositEntity;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.entity.QFileEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.Ilike;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 신청 건 표(V112) — 신청 건 · 자리/금액 줄 · 소재 버전 · 처리 기록 · 입금 확인. 상태를 바꾸는 UPDATE 는 모두 <b>지금 상태를 조건으로</b> 걸고 바뀐 행 수로 이겼는지 돌려준다
 * (서비스는 행을 잠근 뒤 부르지만, 잠그지 않는 길 — 알림 표시 등 — 도 같은 규칙).
 *
 * <p>QueryDSL 이 기본이다 — 읽기는 한 줄 모양(프로젝션, 영속성 문맥의 낡은 엔티티를 보지 않는다), 상태 전이·카운터는 조건부 DML({@code execute() == 1}), 넣기는
 * {@code persist} + {@code flush}({@link JpaStoreWrites}). 잠금은 신청 건 표만 고르는 id 문장의
 * {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE}) 뒤 다시 읽는다 — 신청 건을 FK 로 가리키는 줄(줄·소재·계약서·입금·환불·처리 기록·핫딜 게시·서류 접수)은 그 건을 먼저
 * 잠근 흐름이나 그 건을 만드는 트랜잭션이 넣고(노출 통계 {@code ad_daily_stats} 는 판정에 쓰지 않는다), 신청 건은 지우지 않는다.
 * Native SQL 로 남긴 것과 이유: 화면 번호 만들기({@code nextval} 시퀀스 + 한국 날짜 연도 — DB 시퀀스),
 * 입금 확인 upsert({@code ON CONFLICT (application_id) DO UPDATE} — 같은 건을 두 운영자가 동시에 처음 확인해도 한 줄).
 */
@Repository
public class BizApplicationStore extends RoboyamRepositoryQDSL {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private static final QBizApplicationEntity A = QBizApplicationEntity.bizApplicationEntity;
    private static final QBizApplicationItemEntity I = QBizApplicationItemEntity.bizApplicationItemEntity;
    private static final QBizCreativeVersionEntity C = QBizCreativeVersionEntity.bizCreativeVersionEntity;
    private static final QBizApplicationEventEntity E = QBizApplicationEventEntity.bizApplicationEventEntity;
    private static final QBizDepositEntity D = QBizDepositEntity.bizDepositEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QProfileEntity P = QProfileEntity.profileEntity;
    private static final QFileEntity F = QFileEntity.fileEntity;

    /** 신청 건 한 줄 */
    public record AppRow(long id, String no, BizApplicationKind kind, BizApplicationStatus status, BizSource source, Long businessProfileId, Long userId,
                         String advertiserName, String companyName, String bizNoLast4, String title, Instant startAt, Instant endAt,
                         BizPaymentMethod preferredMethod, BizPaymentMethod paymentMethod, Amount quote, Amount approved, String adjustReason, Instant approvedAt,
                         Long approvedBy, Instant paymentDeadlineAt, Instant depositDeadlineAt, Instant paidAt, Long paymentOrderId, int activeContractCount,
                         Instant depositConfirmedAt, BizRejectCode rejectCode, String rejectText, Instant takenDownAt, BizTakedownCause takedownCause,
                         String takedownText, String adPolicyVersion, String refundPolicyVersion, Instant agreedAt, Instant closedAt, int version,
                         Instant createdAt, Instant updatedAt, Long advertiserId, Long createdBy, int weight, Long proposedSupply, String operatorMemo,
                         Long takenDownBy, String takedownMail, int pauseCount, Instant pausedAt, int pausedMinutes, BizActorType takedownActor,
                         BizPauseChannel takedownChannel, String takedownMemo, Instant stoppedAt, Instant refundFrom, int stopFeeRate) {

        /** 지금 정지 중인가(내림 · 까닭 정지) — 끝 시각이 지났는지는 보지 않는다 */
        public boolean paused() {
            return status == BizApplicationStatus.TAKEN_DOWN && takedownCause == BizTakedownCause.ADVERTISER_PAUSE;
        }

        /** 결제·입금 기한(승인 뒤) */
        public Instant deadlineAt() {
            return paymentDeadlineAt != null ? paymentDeadlineAt : depositDeadlineAt;
        }

        /** 사업자 쪽 표시 이름(상호 또는 광고주) */
        public String businessName() {
            return companyName != null ? companyName : advertiserName;
        }
    }

    /** 새 신청 건 */
    public record NewApp(long id, BizApplicationKind kind, BizSource source, Long businessProfileId, Long userId, String advertiserName, String companyName,
                         String bizNoLast4, String title, Instant startAt, Instant endAt, BizPaymentMethod preferredMethod, Amount quote, String adPolicyVersion,
                         String refundPolicyVersion, Instant agreedAt, int stopFeeRate, Instant now) {
    }

    /** 자리·금액 줄 */
    public record ItemRow(long id, long applicationId, BizProduct product, long unitPrice, int units, long supply, Instant startAt, Instant endAt) {
    }

    /** 소재 버전 */
    public record CreativeRow(long id, long applicationId, int version, Map<String, Object> content, String createdByType, Long createdBy, Instant createdAt,
                              Instant approvedAt) {
    }

    /** 처리 기록 한 줄 — 이름은 운영자 이름 또는 회원 닉네임(없으면 null) */
    public record EventRow(Instant at, String action, BizActorType actorType, Long actorId, String actorName, String note) {
    }

    /** 입금 확인 — 확인자 이름 포함 */
    public record DepositRow(long applicationId, long amount, LocalDate depositedOn, String depositorName, String mismatchReason, long confirmedBy,
                             String confirmedByName, Instant confirmedAt) {
    }

    /** 신청 건 한 줄을 읽는 칸(차례가 {@link #app} 과 같다) */
    private static final Expression<?>[] APP = {A.id, A.applicationNo, A.kind, A.status, A.source, A.businessProfileId, A.userId, A.advertiserName, A.companyName,
            A.bizNoLast4, A.title, A.startAt, A.endAt, A.preferredMethod, A.paymentMethod, A.quoteSupply, A.quoteVat, A.quoteTotal, A.approvedSupply, A.approvedVat,
            A.approvedTotal, A.adjustReason, A.approvedAt, A.approvedBy, A.paymentDeadlineAt, A.depositDeadlineAt, A.paidAt, A.paymentOrderId, A.activeContractCount,
            A.depositConfirmedAt, A.rejectCode, A.rejectText, A.takenDownAt, A.takedownCause, A.takedownText, A.adPolicyVersion, A.refundPolicyVersion, A.agreedAt,
            A.closedAt, A.version, A.createdAt, A.updatedAt, A.advertiserId, A.createdBy, A.weight, A.proposedSupply, A.operatorMemo, A.takenDownBy, A.takedownMail,
            A.pauseCount, A.pausedAt, A.pausedMinutes, A.takedownActor, A.takedownChannel, A.takedownMemo, A.stoppedAt, A.refundFrom, A.stopFeeRate};

    private static final Expression<ItemRow> ITEM = Projections.constructor(ItemRow.class, I.id, I.applicationId, I.product, I.unitPrice, I.units, I.supply, I.startAt,
            I.endAt);

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    public BizApplicationStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query);
        this.entityManager = entityManager;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 신청 건 ─────────────────────────────────────────────────────────────

    /** 넣고 화면 번호(BA-연도-일련번호)를 돌려준다 — 연도는 한국 날짜 */
    public String insert(NewApp a) {
        String no = nextNo(a.now());
        JpaStoreWrites.insert(entityManager, BizApplicationEntity.submitted(a.id(), no, a.kind(), a.source(), a.businessProfileId(), a.userId(), a.advertiserName(),
                a.companyName(), a.bizNoLast4(), a.title(), a.startAt(), a.endAt(), a.preferredMethod(), a.quote().supply(), a.quote().vat(), a.quote().total(),
                a.adPolicyVersion(), a.refundPolicyVersion(), a.agreedAt(), a.stopFeeRate(), a.now()));
        return no;
    }

    /** 운영자 등록 건(04 §2) — 심사 대기 · 결제 방식 MANUAL · 등록자 · 광고주 · 비중 · 계약 금액 제안 · 운영 메모 */
    public record NewOperatorApp(long id, BizApplicationKind kind, Long businessProfileId, Long advertiserId, String advertiserName, String companyName,
                                 String bizNoLast4, String title, Instant startAt, Instant endAt, Amount quote, int weight, long proposedSupply, String memo,
                                 long createdBy, int stopFeeRate, Instant now) {
    }

    /** 운영자 등록 건을 넣고 화면 번호를 돌려준다 — 정책 동의·회원 없음(ck_ba_source_operator) */
    public String insertOperator(NewOperatorApp a) {
        String no = nextNo(a.now());
        JpaStoreWrites.insert(entityManager, BizApplicationEntity.operator(a.id(), no, a.kind(), a.businessProfileId(), a.advertiserId(), a.advertiserName(),
                a.companyName(), a.bizNoLast4(), a.title(), a.startAt(), a.endAt(), a.quote().supply(), a.quote().vat(), a.quote().total(), a.weight(),
                a.proposedSupply(), a.memo(), a.createdBy(), a.stopFeeRate(), a.now()));
        return no;
    }

    /**
     * 화면 번호 {@code BA-<한국 날짜 연도>-<일련번호 5자리>}. Native SQL 인 이유: 일련번호가 DB 시퀀스({@code biz_application_no_seq})다 — 서버가 여러 대여도 겹치지 않는다
     * ({@code lpad} 는 5자리를 넘으면 자른다 — 예전과 같은 문장).
     */
    private String nextNo(Instant now) {
        return jdbc.queryForObject("SELECT 'BA-' || to_char(?::timestamptz AT TIME ZONE 'Asia/Seoul', 'YYYY') || '-' || lpad(nextval('biz_application_no_seq')::text, 5, '0')",
                String.class, Timestamp.from(now));
    }

    public Optional<AppRow> find(long id) {
        return one(A.id.eq(id));
    }

    /** 행을 잠그고 읽는다(처리 — 결제 확정과 겹쳐도 한쪽만) */
    public Optional<AppRow> lock(long id) {
        return query.select(A.id).from(A).where(A.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() != null ? find(id) : Optional.empty();
    }

    /** 그 회원의 신청 건을 잠근다 — 남의 건은 비어 있다(404) */
    public Optional<AppRow> lockOwned(long id, long userId) {
        if (query.select(A.id).from(A).where(A.id.eq(id), A.userId.eq(userId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() == null) {
            return Optional.empty();
        }
        return findOwned(id, userId);
    }

    public Optional<AppRow> findOwned(long id, long userId) {
        return one(A.id.eq(id), A.userId.eq(userId));
    }

    public List<AppRow> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return rows(query.select(APP).from(A).where(A.id.in(ids)).fetch());
    }

    /** 사업자 신원의 신청 건(상태 거르기) — 정지·해제·탈퇴 훅 */
    public List<AppRow> byProfile(long profileId, Set<BizApplicationStatus> statuses) {
        return rows(query.select(APP).from(A).where(A.businessProfileId.eq(profileId), A.status.in(statuses)).orderBy(A.startAt.asc(), A.id.asc()).fetch());
    }

    /** 회원의 신청 건 전부(최근 먼저, 최대 500) — 공개 목록은 묶음별 건수를 함께 낸다 */
    public List<AppRow> byUser(long userId) {
        return rows(query.select(APP).from(A).where(A.userId.eq(userId)).orderBy(A.createdAt.desc(), A.id.desc()).limit(500).fetch());
    }

    /**
     * 관리자 큐 — 도메인(그 도메인의 종류만)·종류·검색(번호·상호·광고주) 거르기. 큐는 상태·기한·환불로 서비스가 가른다(최근 2,000건까지 — 큐별 건수도 이 안에서 센다).
     * 종류는 그 도메인의 것이어야 한다(부르는 쪽이 확인한다).
     */
    public List<AppRow> forAdmin(BizDomain domain, BizApplicationKind kind, String q) {
        List<BooleanExpression> where = new ArrayList<>();
        where.add(A.kind.in(domain.kinds()));
        if (kind != null) {
            where.add(A.kind.eq(kind));
        }
        if (q != null && !q.isBlank()) {
            where.add(search(q));
        }
        return rows(query.select(APP).from(A).where(where.toArray(BooleanExpression[]::new)).orderBy(A.createdAt.desc(), A.id.desc()).limit(2000).fetch());
    }

    /** 번호·상호·광고주·제목 부분 일치(예전과 같은 {@code ILIKE … ESCAPE '\'} — {@link Ilike}, 와일드카드 글자는 글자 그대로). 광고 일정표 장부도 쓴다(기본 별칭 신청 건) */
    public static BooleanExpression search(String q) {
        String term = q.strip();
        return Ilike.contains(A.applicationNo, term).or(Ilike.contains(A.companyName, term)).or(Ilike.contains(A.advertiserName, term)).or(Ilike.contains(A.title, term));
    }

    /** 승인 — 심사 대기에서만. 기간·요금표 계산·확정 금액·결제 방식·기한 */
    public boolean approve(long id, BizApplicationStatus to, Instant start, Instant end, Amount quote, Amount approved, String adjustReason, BizPaymentMethod method,
                           Instant deadline, long operatorId, Instant now) {
        boolean online = method == BizPaymentMethod.ONLINE;
        return bump(query.update(A).set(A.status, to).set(A.startAt, start).set(A.endAt, end).set(A.quoteSupply, quote.supply()).set(A.quoteVat, quote.vat())
                .set(A.quoteTotal, quote.total()).set(A.approvedSupply, approved.supply()).set(A.approvedVat, approved.vat()).set(A.approvedTotal, approved.total())
                .set(A.adjustReason, adjustReason).set(A.approvedAt, now).set(A.approvedBy, operatorId).set(A.paymentMethod, method)
                .set(A.paymentDeadlineAt, online ? deadline : null).set(A.depositDeadlineAt, online ? null : deadline).setNull(A.paymentDueNotifiedAt), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.SUBMITTED)).execute() == 1;
    }

    public boolean reject(long id, BizRejectCode code, String text, Instant now) {
        return bump(query.update(A).set(A.status, BizApplicationStatus.REJECTED).set(A.rejectCode, code).set(A.rejectText, text).set(A.closedAt, now), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.SUBMITTED)).execute() == 1;
    }

    /** 상태만 바꾼다(끝난 상태면 닫은 시각). 지금 상태가 {@code from} 이어야 한다 */
    public boolean move(long id, BizApplicationStatus from, BizApplicationStatus to, Instant now) {
        JPAUpdateClause update = query.update(A).set(A.status, to);
        if (BizApplicationStatus.CLOSED.contains(to)) {
            update.set(A.closedAt, now);
        }
        return bump(update, now).where(A.id.eq(id), A.status.eq(from)).execute() == 1;
    }

    /** 승인 뒤 결제 전 소재 바꾸기 → 다시 심사 대기 — 승인 스냅샷·결제 방식·기한을 비운다(자리 반납) */
    public boolean backToReview(long id, BizApplicationStatus from, String title, Instant now) {
        return bump(query.update(A).set(A.status, BizApplicationStatus.SUBMITTED).set(A.title, title).setNull(A.approvedSupply).setNull(A.approvedVat)
                .setNull(A.approvedTotal).setNull(A.adjustReason).setNull(A.approvedAt).setNull(A.approvedBy).setNull(A.paymentMethod).setNull(A.paymentDeadlineAt)
                .setNull(A.depositDeadlineAt).setNull(A.paymentDueNotifiedAt), now).where(A.id.eq(id), A.status.eq(from)).execute() == 1;
    }

    public boolean retitle(long id, String title, Instant now) {
        return bump(query.update(A).set(A.title, title), now).where(A.id.eq(id)).execute() == 1;
    }

    /** 결제 대기 → 수동(입금 대기) */
    public boolean switchManual(long id, Instant depositDeadline, Instant now) {
        return bump(query.update(A).set(A.status, BizApplicationStatus.AWAITING_DEPOSIT).set(A.paymentMethod, BizPaymentMethod.MANUAL).setNull(A.paymentDeadlineAt)
                .set(A.depositDeadlineAt, depositDeadline), now).where(A.id.eq(id), A.status.eq(BizApplicationStatus.AWAITING_PAYMENT)).execute() == 1;
    }

    /** 온라인 결제 완료 → 예약 */
    public boolean markPaid(long id, Instant paidAt, long paymentOrderId, Instant now) {
        return bump(query.update(A).set(A.status, BizApplicationStatus.SCHEDULED).set(A.paidAt, paidAt).set(A.paymentOrderId, paymentOrderId), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.AWAITING_PAYMENT), A.paymentMethod.eq(BizPaymentMethod.ONLINE)).execute() == 1;
    }

    public void setContractCount(long id, int count, Instant now) {
        query.update(A).set(A.activeContractCount, count).set(A.updatedAt, now).where(A.id.eq(id)).execute();
    }

    /** 계약서 종이 근거 수(V140 — 잘못 기록 아닌 종이 접수, 폐기한 줄 포함) — 수동 확정 CHECK 가 파일 수와 더해 본다 */
    public void setPaperContractCount(long id, int count, Instant now) {
        query.update(A).set(A.paperContractCount, count).set(A.updatedAt, now).where(A.id.eq(id)).execute();
    }

    public void setDepositConfirmed(long id, Instant at, Instant now) {
        query.update(A).set(A.depositConfirmedAt, at).set(A.updatedAt, now).where(A.id.eq(id)).execute();
    }

    public boolean setDepositDeadline(long id, Instant deadline, Instant now) {
        return bump(query.update(A).set(A.depositDeadlineAt, deadline), now).where(A.id.eq(id), A.status.eq(BizApplicationStatus.AWAITING_DEPOSIT)).execute() == 1;
    }

    /** 예약 → 내림 */
    public boolean takeDown(long id, BizTakedownCause cause, String text, Instant now) {
        return bump(query.update(A).set(A.status, BizApplicationStatus.TAKEN_DOWN).set(A.takenDownAt, now).set(A.takedownCause, cause).set(A.takedownText, text), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.SCHEDULED)).execute() == 1;
    }

    /** 예약 → 긴급 내림(운영자 — 사유 · 내린 운영자) */
    public boolean takeDownEmergency(long id, String text, long operatorId, Instant now) {
        return bump(query.update(A).set(A.status, BizApplicationStatus.TAKEN_DOWN).set(A.takenDownAt, now).set(A.takedownCause, BizTakedownCause.EMERGENCY)
                .set(A.takedownText, text).set(A.takenDownBy, operatorId).setNull(A.takedownMail), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.SCHEDULED)).execute() == 1;
    }

    /** 긴급 내림 메일 결과(SENT·FAILED·NO_ADDRESS) — 아직 내림 상태일 때만 */
    public void setTakedownMail(long id, String result) {
        query.update(A).set(A.takedownMail, result).where(A.id.eq(id), A.status.eq(BizApplicationStatus.TAKEN_DOWN)).execute();
    }

    /** 내림 → 예약(끝 시각을 바꿀 수 있다 — 연장 후 되돌리기) */
    public boolean restore(long id, Instant end, Instant now) {
        return bump(clearTakedown(query.update(A).set(A.status, BizApplicationStatus.SCHEDULED).set(A.endAt, end)).setNull(A.endNotifiedAt), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.TAKEN_DOWN), A.takedownCause.in(BizTakedownCause.SUSPENDED, BizTakedownCause.EMERGENCY))
                .execute() == 1;
    }

    // ── 광고주 요청 정지·중단(V127 — biz-pause-contract) ───────────────────

    /** 누가 정지·중단했나 — 사업자(회원) 또는 운영자 대신(경로·메모 필수) */
    public record PauseActor(BizActorType type, Long operatorId, BizPauseChannel channel, String memo) {
    }

    /** 진행 중 예약 → 정지(내림 · 까닭 정지 · 멈춘 시각 · 정지 횟수 + 1). 한도는 조건에 같이 건다 */
    public boolean pause(long id, PauseActor by, int limit, Instant now) {
        return bump(query.update(A).set(A.status, BizApplicationStatus.TAKEN_DOWN).set(A.takenDownAt, now).set(A.takedownCause, BizTakedownCause.ADVERTISER_PAUSE)
                .setNull(A.takedownText).set(A.takenDownBy, by.operatorId()).setNull(A.takedownMail).set(A.takedownActor, by.type())
                .set(A.takedownChannel, by.channel()).set(A.takedownMemo, by.memo()).set(A.pausedAt, now).set(A.pauseCount, A.pauseCount.add(1)), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.SCHEDULED), A.pauseCount.lt(limit)).execute() == 1;
    }

    /** 정지 → 예약(끝 시각 그대로 — 멈춘 분을 더한다). 내림 칸을 비운다 */
    public boolean resume(long id, int addMinutes, Instant now) {
        return bump(clearTakedown(query.update(A).set(A.status, BizApplicationStatus.SCHEDULED)).set(A.pausedMinutes, A.pausedMinutes.add(addMinutes)), now)
                .where(A.id.eq(id), A.status.eq(BizApplicationStatus.TAKEN_DOWN), A.takedownCause.eq(BizTakedownCause.ADVERTISER_PAUSE)).execute() == 1;
    }

    /**
     * 진행 중 예약 또는 정지 → 중단(내림 · 까닭 중단 · 사유 · 중단 시각 · 일할 기준 시각). 정지 중 전환이면 그 정지의 멈춘 분을 더한다. 지금 상태가 {@code from} 이어야 한다
     * (정지 중이면 까닭도 정지).
     */
    public boolean stop(long id, BizApplicationStatus from, PauseActor by, String reason, Instant refundFrom, int addMinutes, Instant now) {
        BooleanExpression guard = A.id.eq(id).and(A.status.eq(from));
        if (from == BizApplicationStatus.TAKEN_DOWN) {
            guard = guard.and(A.takedownCause.eq(BizTakedownCause.ADVERTISER_PAUSE));
        }
        return bump(query.update(A).set(A.status, BizApplicationStatus.TAKEN_DOWN).set(A.takenDownAt, now).set(A.takedownCause, BizTakedownCause.ADVERTISER_STOP)
                .set(A.takedownText, reason).set(A.takenDownBy, by.operatorId()).setNull(A.takedownMail).set(A.takedownActor, by.type())
                .set(A.takedownChannel, by.channel()).set(A.takedownMemo, by.memo()).setNull(A.pausedAt).set(A.pausedMinutes, A.pausedMinutes.add(addMinutes))
                .set(A.stoppedAt, now).set(A.refundFrom, refundFrom), now).where(guard).execute() == 1;
    }

    /** 알림 보낸 표시 — 아직 안 보냈을 때만(배치가 겹쳐도 한 번) */
    public boolean markNotified(long id, String column, Instant now) {
        DateTimePath<Instant> path = switch (column) {
            case "payment_due_notified_at" -> A.paymentDueNotifiedAt;
            case "start_notified_at" -> A.startNotifiedAt;
            case "end_notified_at" -> A.endNotifiedAt;
            default -> throw new IllegalArgumentException(column);
        };
        return query.update(A).set(path, now).where(A.id.eq(id), path.isNull()).execute() == 1;
    }

    // ── 배치 대상 ───────────────────────────────────────────────────────────

    /** 결제 기한이 지난 결제 대기 — ID 키셋(처리하면 대상이 아니게 되어 다시 고르지 않는다) */
    public List<AppRow> paymentOverdue(Instant now, long afterId, int limit) {
        return keyset(limit, A.status.eq(BizApplicationStatus.AWAITING_PAYMENT), A.paymentDeadlineAt.loe(now), A.id.gt(afterId));
    }

    /** 결제 기한이 {@code until} 안이고 아직 알리지 않은 결제 대기 */
    public List<AppRow> paymentDueSoon(Instant now, Instant until, long afterId, int limit) {
        return keyset(limit, A.status.eq(BizApplicationStatus.AWAITING_PAYMENT), A.paymentDeadlineAt.gt(now), A.paymentDeadlineAt.loe(until),
                A.paymentDueNotifiedAt.isNull(), A.id.gt(afterId));
    }

    /** 시작이 {@code until} 안인 예약(아직 알리지 않음) */
    public List<AppRow> startingSoon(Instant now, Instant until, long afterId, int limit) {
        return keyset(limit, A.status.eq(BizApplicationStatus.SCHEDULED), A.startAt.gt(now), A.startAt.loe(until), A.startNotifiedAt.isNull(), A.id.gt(afterId));
    }

    /** 끝이 {@code until} 안인 진행 중 예약(아직 알리지 않음) */
    public List<AppRow> endingSoon(Instant now, Instant until, long afterId, int limit) {
        return keyset(limit, A.status.eq(BizApplicationStatus.SCHEDULED), A.startAt.loe(now), A.endAt.gt(now), A.endAt.loe(until), A.endNotifiedAt.isNull(),
                A.id.gt(afterId));
    }

    // ── 자리·금액 줄 ────────────────────────────────────────────────────────

    public void insertItem(ItemRow item, Instant now) {
        JpaStoreWrites.insert(entityManager, itemEntity(item, now));
    }

    /** 줄을 통째로 바꾼다(승인 때 기간·금액을 다시 계산) */
    public void replaceItems(long applicationId, List<ItemRow> items, Instant now) {
        query.delete(I).where(I.applicationId.eq(applicationId)).execute();
        JpaStoreWrites.insertAll(entityManager, items.stream().map(item -> itemEntity(item, now)).toList());
    }

    public List<ItemRow> items(long applicationId) {
        return query.select(ITEM).from(I).where(I.applicationId.eq(applicationId)).orderBy(I.product.asc()).fetch();
    }

    public Map<Long, List<ItemRow>> items(Collection<Long> applicationIds) {
        Map<Long, List<ItemRow>> out = new HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        query.select(ITEM).from(I).where(I.applicationId.in(applicationIds)).orderBy(I.product.asc()).fetch()
                .forEach(row -> out.computeIfAbsent(row.applicationId(), k -> new ArrayList<>()).add(row));
        return out;
    }

    /** 끝 시각만 바꾼다(되돌리기 연장) */
    public void extendItems(long applicationId, Instant end) {
        query.update(I).set(I.endAt, end).where(I.applicationId.eq(applicationId)).execute();
    }

    // ── 소재 버전 ───────────────────────────────────────────────────────────

    public void insertCreative(long id, long applicationId, int version, Map<String, Object> content, BizActorType byType, Long by, Instant now) {
        JpaStoreWrites.insert(entityManager, BizCreativeVersionEntity.create(id, applicationId, version, JSON.writeValueAsString(content), byType, by, now));
    }

    /** 소재 버전 전부(오래된 것 먼저) — 광고 일정표 상세 */
    public List<CreativeRow> creatives(long applicationId) {
        return query.select(C.id, C.applicationId, C.version, C.content, C.createdByType, C.createdBy, C.createdAt, C.approvedAt).from(C)
                .where(C.applicationId.eq(applicationId)).orderBy(C.version.asc()).fetch().stream().map(BizApplicationStore::creative).toList();
    }

    public Optional<CreativeRow> latestCreative(long applicationId) {
        Tuple t = query.select(C.id, C.applicationId, C.version, C.content, C.createdByType, C.createdBy, C.createdAt, C.approvedAt).from(C)
                .where(C.applicationId.eq(applicationId)).orderBy(C.version.desc()).fetchFirst();
        return Optional.ofNullable(t).map(BizApplicationStore::creative);
    }

    /** 건마다 가장 높은 버전 — 버전은 건 안에서 유일하다(ux_biz_creatives) */
    public Map<Long, CreativeRow> latestCreatives(Collection<Long> applicationIds) {
        Map<Long, CreativeRow> out = new HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        QBizCreativeVersionEntity v = new QBizCreativeVersionEntity("v");
        query.select(C.id, C.applicationId, C.version, C.content, C.createdByType, C.createdBy, C.createdAt, C.approvedAt).from(C)
                .where(C.applicationId.in(applicationIds), C.version.eq(JPAExpressions.select(v.version.max()).from(v).where(v.applicationId.eq(C.applicationId))))
                .fetch().forEach(t -> {
                    CreativeRow row = creative(t);
                    out.put(row.applicationId(), row);
                });
        return out;
    }

    /** 승인 때 지금 소재 버전(가장 높은 버전)을 고정한다(A2) */
    public void approveLatestCreative(long applicationId, Instant now) {
        QBizCreativeVersionEntity v = new QBizCreativeVersionEntity("v");
        query.update(C).set(C.approvedAt, now)
                .where(C.applicationId.eq(applicationId), C.version.eq(JPAExpressions.select(v.version.max()).from(v).where(v.applicationId.eq(applicationId))))
                .execute();
    }

    // ── 처리 기록 ───────────────────────────────────────────────────────────

    public void insertEvent(long id, long applicationId, BizEventAction action, BizActorType actorType, Long actorId, String note, Instant now) {
        JpaStoreWrites.insert(entityManager, BizApplicationEventEntity.create(id, applicationId, action, actorType, actorId, note == null ? null : cut(note, 300), now));
    }

    /** 처리 기록(오래된 것 먼저, 최대 300) — 행위자 이름은 운영자 이름·회원 닉네임 */
    public List<EventRow> events(long applicationId) {
        return query.select(E.createdAt, E.action, E.actorType, E.actorId, O.operatorName, P.nickname, E.note).from(E)
                .leftJoin(O).on(E.actorType.eq(BizActorType.OPERATOR), O.id.eq(E.actorId))
                .leftJoin(P).on(E.actorType.eq(BizActorType.MEMBER), P.userId.eq(E.actorId))
                .where(E.applicationId.eq(applicationId)).orderBy(E.createdAt.asc(), E.id.asc()).limit(300).fetch().stream()
                .map(t -> {
                    BizActorType type = t.get(E.actorType);
                    String name = type == BizActorType.OPERATOR ? t.get(O.operatorName) : type == BizActorType.MEMBER ? t.get(P.nickname) : null;
                    return new EventRow(t.get(E.createdAt), t.get(E.action), type, t.get(E.actorId), name, t.get(E.note));
                }).toList();
    }

    public int countEvents(long applicationId, BizEventAction action) {
        Long n = query.select(E.count()).from(E).where(E.applicationId.eq(applicationId), E.action.eq(action.name())).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    // ── 입금 확인 ───────────────────────────────────────────────────────────

    /** 입금 확인 넣기·고치기. Native SQL 인 이유: {@code ON CONFLICT (application_id) DO UPDATE} upsert — 확인과 정정이 한 문장이고 동시에 처음 확인해도 한 줄이다 */
    public void upsertDeposit(long applicationId, long amount, LocalDate depositedOn, String depositorName, String mismatchReason, long operatorId, Instant now) {
        jdbc.update("INSERT INTO biz_deposits (application_id, amount, deposited_on, depositor_name, mismatch_reason, confirmed_by, confirmed_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (application_id) DO UPDATE SET amount = EXCLUDED.amount, deposited_on = EXCLUDED.deposited_on, depositor_name = EXCLUDED.depositor_name, "
                        + "mismatch_reason = EXCLUDED.mismatch_reason, confirmed_by = EXCLUDED.confirmed_by, confirmed_at = EXCLUDED.confirmed_at",
                applicationId, amount, java.sql.Date.valueOf(depositedOn), depositorName, mismatchReason, operatorId, Timestamp.from(now));
    }

    public Optional<DepositRow> deposit(long applicationId) {
        Tuple t = query.select(D.applicationId, D.amount, D.depositedOn, D.depositorName, D.mismatchReason, D.confirmedBy, O.operatorName, D.confirmedAt).from(D)
                .leftJoin(O).on(O.id.eq(D.confirmedBy)).where(D.applicationId.eq(applicationId)).fetchFirst();
        return Optional.ofNullable(t).map(r -> new DepositRow(r.get(D.applicationId), r.get(D.amount), r.get(D.depositedOn), r.get(D.depositorName),
                r.get(D.mismatchReason), r.get(D.confirmedBy), r.get(O.operatorName), r.get(D.confirmedAt)));
    }

    // ── 이름 ────────────────────────────────────────────────────────────────

    /** 운영자 이름(없으면 null) */
    public String operatorName(Long operatorId) {
        if (operatorId == null) {
            return null;
        }
        return query.select(O.operatorName).from(O).where(O.id.eq(operatorId)).fetchFirst();
    }

    /** 회원 닉네임(없으면 null) */
    public String nickname(Long userId) {
        if (userId == null) {
            return null;
        }
        return query.select(P.nickname).from(P).where(P.userId.eq(userId)).fetchFirst();
    }

    // ── 광고 이미지(files — 용도 BIZ_AD_IMAGE) ─────────────────────────────

    /** 회원이 올린 광고 이미지 — 주소 키·이름·가로·세로(배너 비율 검사, 모르면 null). 올린 사람이 다르거나 없으면 비어 있다 */
    public record AdImage(long id, String storageKey, String contentType, long sizeBytes, Integer width, Integer height) {
    }

    private static final Expression<AdImage> AD_IMAGE = Projections.constructor(AdImage.class, F.id, F.storageKey, F.contentType, F.sizeBytes, F.width, F.height);

    public Optional<AdImage> adImage(long fileId, long userId) {
        return Optional.ofNullable(query.select(AD_IMAGE).from(F).where(F.id.eq(fileId), F.uploadedByUser.eq(userId), F.purpose.eq(FilePurpose.BIZ_AD_IMAGE))
                .fetchFirst());
    }

    /** 운영자가 올린 광고 이미지(광고 일정표 수동 등록 — {@code POST /api/admin/ads/images}) */
    public Optional<AdImage> operatorAdImage(long fileId) {
        return Optional.ofNullable(query.select(AD_IMAGE).from(F).where(F.id.eq(fileId), F.uploadedBy.isNotNull(), F.purpose.eq(FilePurpose.BIZ_AD_IMAGE)).fetchFirst());
    }

    /** 쓰는 중으로(올리다 만 파일 정리에서 빠진다) — 처음 쓴 시각은 그대로 */
    public void markImageUsed(long fileId, Instant now) {
        query.update(F).set(F.status, FileStatus.IN_USE).set(F.usedAt, F.usedAt.coalesce(now)).where(F.id.eq(fileId)).execute();
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private Optional<AppRow> one(BooleanExpression... where) {
        return Optional.ofNullable(query.select(APP).from(A).where(where).fetchFirst()).map(BizApplicationStore::app);
    }

    private List<AppRow> keyset(int limit, BooleanExpression... where) {
        return rows(query.select(APP).from(A).where(where).orderBy(A.id.asc()).limit(limit).fetch());
    }

    /** 바뀐 시각 + {@code version + 1} */
    private static JPAUpdateClause bump(JPAUpdateClause update, Instant now) {
        return update.set(A.updatedAt, now).set(A.version, A.version.add(1));
    }

    /** 내림 칸 비우기(되돌리기·다시 켜기) */
    private static JPAUpdateClause clearTakedown(JPAUpdateClause update) {
        return update.setNull(A.takenDownAt).setNull(A.takedownCause).setNull(A.takedownText).setNull(A.takenDownBy).setNull(A.takedownMail)
                .setNull(A.takedownActor).setNull(A.takedownChannel).setNull(A.takedownMemo).setNull(A.pausedAt);
    }

    private static BizApplicationItemEntity itemEntity(ItemRow item, Instant now) {
        return BizApplicationItemEntity.create(item.id(), item.applicationId(), item.product(), item.unitPrice(), item.units(), item.supply(), item.startAt(),
                item.endAt(), now);
    }

    private static List<AppRow> rows(List<Tuple> tuples) {
        return tuples.stream().map(BizApplicationStore::app).toList();
    }

    private static AppRow app(Tuple t) {
        Long approvedTotal = t.get(A.approvedTotal);
        return new AppRow(t.get(A.id), t.get(A.applicationNo), t.get(A.kind), t.get(A.status), t.get(A.source), t.get(A.businessProfileId), t.get(A.userId),
                t.get(A.advertiserName), t.get(A.companyName), t.get(A.bizNoLast4), t.get(A.title), t.get(A.startAt), t.get(A.endAt), t.get(A.preferredMethod),
                t.get(A.paymentMethod), new Amount(n(t.get(A.quoteSupply)), n(t.get(A.quoteVat)), n(t.get(A.quoteTotal))),
                approvedTotal == null ? null : new Amount(n(t.get(A.approvedSupply)), n(t.get(A.approvedVat)), approvedTotal), t.get(A.adjustReason),
                t.get(A.approvedAt), t.get(A.approvedBy), t.get(A.paymentDeadlineAt), t.get(A.depositDeadlineAt), t.get(A.paidAt), t.get(A.paymentOrderId),
                n(t.get(A.activeContractCount)), t.get(A.depositConfirmedAt), t.get(A.rejectCode), t.get(A.rejectText), t.get(A.takenDownAt), t.get(A.takedownCause),
                t.get(A.takedownText), t.get(A.adPolicyVersion), t.get(A.refundPolicyVersion), t.get(A.agreedAt), t.get(A.closedAt), n(t.get(A.version)),
                t.get(A.createdAt), t.get(A.updatedAt), t.get(A.advertiserId), t.get(A.createdBy), n(t.get(A.weight)), t.get(A.proposedSupply), t.get(A.operatorMemo),
                t.get(A.takenDownBy), t.get(A.takedownMail), n(t.get(A.pauseCount)), t.get(A.pausedAt), n(t.get(A.pausedMinutes)), t.get(A.takedownActor),
                t.get(A.takedownChannel), t.get(A.takedownMemo), t.get(A.stoppedAt), t.get(A.refundFrom), n(t.get(A.stopFeeRate)));
    }

    private static CreativeRow creative(Tuple t) {
        return new CreativeRow(t.get(C.id), t.get(C.applicationId), n(t.get(C.version)), JSON.readValue(t.get(C.content), MAP), t.get(C.createdByType),
                t.get(C.createdBy), t.get(C.createdAt), t.get(C.approvedAt));
    }

    static int n(Integer value) {
        return value == null ? 0 : value;
    }

    static long n(Long value) {
        return value == null ? 0 : value;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
