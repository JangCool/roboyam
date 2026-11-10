package com.community.sanction.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.report.entity.QReportCaseEntity;
import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.MemberSanctionEntity;
import com.community.sanction.entity.QMemberSanctionEntity;
import com.community.sanction.entity.QSanctionAppealEntity;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.entity.SanctionStatus;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 사이트 제재({@code member_sanctions})·이의제기({@code sanction_appeals}) 읽기·쓰기 — shared V136. 서비스({@code MemberSanctionService})와 관리자 목록 저장소가 부른다.
 * 트랜잭션은 부르는 서비스가 연다. 판정 시각은 DB 의 {@code now()} 가 아니라 서비스가 넘기는 지금이다.
 *
 * <p>QueryDSL 이 기본이다 — 회원 행 쓰기 잠금({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 이 잠금 뒤의
 * 판정이 보는 제재 줄은 모두 회원 행을 먼저 잠근 흐름이 넣고 고친다. 이의제기 접수는 회원 행을 잠그지 않지만 잠근 쪽의 판정은 이의제기 줄을 보지 않는다 — 답변은 이의제기 행을
 * id 로 먼저 잠근다) 아래에서 조건부 대량 UPDATE 로 한 번만 이기게 하고(해제 · 인용 · 만료), 진행 중 정지의 판정을
 * 부분 색인({@code ux_member_sanctions_running} · {@code ix_member_sanctions_expire})과 같은 조건으로 둔다. 읽기는 한 줄 모양(프로젝션)이라 영속성 문맥의 낡은 엔티티를 보지 않는다.
 * Native SQL 로 남긴 것과 이유: 이의제기 접수({@code ON CONFLICT (sanction_id) DO NOTHING RETURNING}), 경고 번호 다시 매기기(윈도 함수 {@code row_number} + {@code UPDATE … FROM}),
 * 게시판 지정 끝내기({@code DELETE … RETURNING} — 지운 줄과 알린 줄이 같은 문장이어야 한다).
 *
 * <p>⚠ {@link #endDue}·{@link #releaseMember} 는 DB 배치 {@code R__batch_39_sanction_expire.sql} 과 같은 판정이다(이중 구현) — 한쪽을 고치면 다른 쪽도 고친다.
 */
@Repository
public class MemberSanctionStore extends RoboyamRepositoryQDSL {

    /** 제재 — 한 줄 모양의 별칭(관리자 목록 저장소가 거르기에 쓴다) */
    public static final QMemberSanctionEntity S = QMemberSanctionEntity.memberSanctionEntity;
    /** 대상 회원의 프로필(닉네임) */
    public static final QProfileEntity P = QProfileEntity.profileEntity;
    /** 이의제기 */
    public static final QSanctionAppealEntity A = QSanctionAppealEntity.sanctionAppealEntity;

    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QOperatorEntity ISSUED = new QOperatorEntity("issuedOperator");
    private static final QOperatorEntity LIFTED = new QOperatorEntity("liftedOperator");
    private static final QOperatorEntity ANSWERED = new QOperatorEntity("answeredOperator");
    private static final QReportCaseEntity CASE = QReportCaseEntity.reportCaseEntity;
    /** 하위 질의용 별칭 */
    private static final QMemberSanctionEntity X = new QMemberSanctionEntity("x");

    /** 제재 한 줄 */
    public record SanctionRow(long id, long no, long userId, SanctionKind kind, SanctionStatus status, Integer warningNo, boolean skipped, String reason,
                              Instant startsAt, Instant endsAt, Long issuedBy, String issuedByName, Long reportCaseId, Long reportCaseNo, Instant acknowledgedAt,
                              Long liftedBy, String liftedByName, Instant liftedAt, String liftReason, Instant voidedAt, Instant endedAt, Instant createdAt,
                              Long appealId, Long appealNo, AppealStatus appealStatus, String nickname, String gradeKey, String userStatus, boolean sealed) {

        /** {@code now} 의 상태 — 진행 중인데 끝이 지난 기간 정지는 ENDED(크론이 1분 안에 그렇게 만든다) */
        public SanctionStatus effectiveStatus(Instant now) {
            return status == SanctionStatus.ACTIVE && due(now) ? SanctionStatus.ENDED : status;
        }

        /** 끝난 시각 — 아직 만료 처리 전이면 끝 시각 */
        public Instant effectiveEndedAt(Instant now) {
            return endedAt == null && status == SanctionStatus.ACTIVE && due(now) ? endsAt : endedAt;
        }

        /** {@code now} 에 진행 중인 정지·영구 정지인가 */
        public boolean runningAt(Instant now) {
            return kind.restricts() && effectiveStatus(now) == SanctionStatus.ACTIVE;
        }

        public boolean valid() {
            return status != SanctionStatus.VOIDED;
        }

        private boolean due(Instant now) {
            return endsAt != null && !endsAt.isAfter(now);
        }
    }

    /**
     * 이의제기 한 줄 — 사이트 제재({@code sanctionId}) · 게시판 제재({@code boardSanctionId}, V137) 중 하나만 있다.
     */
    public record AppealRow(long id, long no, Long sanctionId, long userId, AppealStatus status, String reason, String answer, Long answeredBy, String answeredByName,
                            Instant answeredAt, Instant createdAt, Long boardSanctionId) {

        /** 게시판 제재 이의제기인가 */
        public boolean board() {
            return boardSanctionId != null;
        }
    }

    /**
     * 제재 대상 회원.
     *
     * @param gone 탈퇴(WITHDRAWN · 지워짐) 또는 탈퇴 진행 중(WITHDRAWING) — 제재할 수 없다
     */
    public record MemberRow(long id, String status, boolean gone, String nickname, String gradeKey) {
    }

    /** 끝난 게시판 매니저·스텝 지정 한 줄 */
    public record StaffRow(long boardId, String role) {
    }

    private final MemberSanctionRepository sanctions;
    private final EntityManager entityManager;

    public MemberSanctionStore(JPAQueryFactory query, MemberSanctionRepository sanctions, EntityManager entityManager) {
        super(query);
        this.sanctions = sanctions;
        this.entityManager = entityManager;
    }

    // ── 조건 ──────────────────────────────────────────────────────────────

    /** 진행 중인 정지·영구 정지(부분 유일 색인 {@code ux_member_sanctions_running} 과 같은 조건) — 별칭 {@code s} */
    public static BooleanExpression runningStored(QMemberSanctionEntity s) {
        return s.status.eq(SanctionStatus.ACTIVE).and(s.kind.ne(SanctionKind.WARNING));
    }

    /** 그 회원에게 진행 중인 정지·영구 정지가 있다(저장된 상태 기준 — R__batch_39 와 같다) */
    private static BooleanExpression hasRunning(long userId) {
        return JPAExpressions.selectOne().from(X).where(X.userId.eq(userId), runningStored(X)).exists();
    }

    /** 그 회원의 제재 기록이 있다 */
    private static BooleanExpression hasAny(long userId) {
        return JPAExpressions.selectOne().from(X).where(X.userId.eq(userId)).exists();
    }

    // ── 회원 ──────────────────────────────────────────────────────────────

    public Optional<MemberRow> member(long userId) {
        return Optional.ofNullable(memberQuery(userId).fetchOne()).map(MemberSanctionStore::member);
    }

    /**
     * 회원 행을 쓰기 잠금으로 잡고({@code FOR NO KEY UPDATE}) 읽는다 — 부여·해제·인용·만료가 같은 회원에서 줄을 선다(차례: 회원 행 → 제재 행).
     * 잠금은 회원 표만 고르는 한 문장으로 잡고, 칸은 그 뒤 한 줄 모양(프로젝션)으로 읽는다 — 프로필을 잇는 프로젝션에 잠금을 걸면 회원 행이 잠기지 않는다(2026-11-07 실측),
     * 그리고 먼저 읽힌 회원 엔티티가 있어도 잠근 뒤의 값을 본다.
     */
    public Optional<MemberRow> lockMember(long userId) {
        if (query.select(U.id).from(U).where(U.id.eq(userId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() == null) {
            return Optional.empty();
        }
        return member(userId);
    }

    private JPAQuery<Tuple> memberQuery(long userId) {
        return query.select(U.id, U.status, U.deletedAt, P.nickname, P.gradeKey).from(U).leftJoin(P).on(P.userId.eq(U.id)).where(U.id.eq(userId));
    }

    /** 계정을 막는 상태로 바꾼다(SUSPENDED · BANNED) — 회원 행을 잠근 뒤 */
    public void restrictMember(long userId, String status, Instant now) {
        query.update(U).set(U.status, UserStatus.valueOf(status)).set(U.updatedAt, now)
                .where(U.id.eq(userId), U.status.in(UserStatus.ACTIVE, UserStatus.SUSPENDED, UserStatus.BANNED)).execute();
    }

    /**
     * 진행 중인 정지·영구 정지가 없으면 정상(ACTIVE)으로 되돌린다 — 상태가 SUSPENDED·BANNED 이고 제재 기록이 있는 회원만(탈퇴 진행 중은 그대로).
     * R__batch_39 규칙 1·2 와 같은 판정. 회원 행을 잠근 뒤 부른다.
     *
     * @return 되돌렸으면 true
     */
    public boolean releaseMember(long userId, Instant now) {
        return query.update(U).set(U.status, UserStatus.ACTIVE).set(U.updatedAt, now)
                .where(U.id.eq(userId), U.status.in(UserStatus.SUSPENDED, UserStatus.BANNED), hasAny(userId), hasRunning(userId).not()).execute() == 1;
    }

    /** 읽기 전 마감이 할 일이 있나 — 끝이 지난 진행 중 정지가 있거나, 막힌 상태인데 진행 중 정지·영구 정지가 없다(제재 기록이 있는 회원). 회원 범위 색인만 읽는다 */
    public boolean needsSettle(long userId, Instant now) {
        // 예전과 같이 한 문장(두 판정이 같은 시점) — 제재 줄은 회원 줄을 외래키로 가리켜, 회원 줄에서 시작해도 「끝이 지난 정지가 있다」는 같은 결과다
        BooleanExpression due = JPAExpressions.selectOne().from(S)
                .where(S.userId.eq(userId), S.status.eq(SanctionStatus.ACTIVE), S.endsAt.isNotNull(), S.endsAt.loe(now)).exists();
        BooleanExpression stuck = U.status.in(UserStatus.SUSPENDED, UserStatus.BANNED).and(hasAny(userId)).and(hasRunning(userId).not());
        return query.selectOne().from(U).where(U.id.eq(userId), due.or(stuck)).fetchFirst() != null;
    }

    /** 끝이 지난 진행 중 기간 정지를 끝낸다(ended_at = 끝 시각) — R__batch_39 규칙 1 과 같은 판정. 끝낸 수 */
    public int endDue(long userId, Instant now) {
        return (int) query.update(S).set(S.status, SanctionStatus.ENDED).set(S.endedAt, S.endsAt).set(S.updatedAt, now)
                .where(S.userId.eq(userId), S.status.eq(SanctionStatus.ACTIVE), S.endsAt.isNotNull(), S.endsAt.loe(now)).execute();
    }

    /**
     * 그 회원의 게시판 매니저·스텝 지정을 모두 끝낸다(정지 시작 — operator-permissions.md 「자동 해제」). 끝낸 지정.
     *
     * <p>Native SQL 인 이유: {@code DELETE … RETURNING} — 지운 줄과 감사에 남길 줄이 같은 문장의 결과여야 한다(나누면 그 사이에 바뀐 지정이 어긋난다).
     */
    public List<StaffRow> endBoardStaff(long userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("DELETE FROM board_staff WHERE user_id = ?1 RETURNING board_id, role").setParameter(1, userId)
                .getResultList();
        return rows.stream().map(r -> new StaffRow(((Number) r[0]).longValue(), (String) r[1])).toList();
    }

    // ── 제재 ──────────────────────────────────────────────────────────────

    /** 제재 한 줄 질의(회원 닉네임·등급·상태 · 운영자 이름 · 신고 사건 번호 · 이의제기 요약을 함께) — 거르기·차례는 부르는 쪽이 붙인다 */
    public JPAQuery<Tuple> sanctionRows() {
        return query.select(S.id, S.sanctionNo, S.userId, S.kind, S.status, S.warningNo, S.skipped, S.reason, S.startsAt, S.endsAt, S.issuedBy, ISSUED.operatorName,
                        S.reportCaseId, CASE.caseNo, S.acknowledgedAt, S.liftedBy, LIFTED.operatorName, S.liftedAt, S.liftReason, S.voidedAt, S.endedAt, S.createdAt,
                        A.id, A.appealNo, A.status, P.nickname, P.gradeKey, U.status, S.sealed)
                .from(S).join(U).on(U.id.eq(S.userId)).leftJoin(P).on(P.userId.eq(S.userId))
                .leftJoin(ISSUED).on(ISSUED.id.eq(S.issuedBy)).leftJoin(LIFTED).on(LIFTED.id.eq(S.liftedBy))
                .leftJoin(CASE).on(CASE.id.eq(S.reportCaseId)).leftJoin(A).on(A.sanctionId.eq(S.id));
    }

    public static SanctionRow row(Tuple t) {
        UserStatus userStatus = t.get(U.status);
        return new SanctionRow(t.get(S.id), t.get(S.sanctionNo), t.get(S.userId), t.get(S.kind), t.get(S.status), t.get(S.warningNo), t.get(S.skipped),
                t.get(S.reason), t.get(S.startsAt), t.get(S.endsAt), t.get(S.issuedBy), t.get(ISSUED.operatorName), t.get(S.reportCaseId), t.get(CASE.caseNo),
                t.get(S.acknowledgedAt), t.get(S.liftedBy), t.get(LIFTED.operatorName), t.get(S.liftedAt), t.get(S.liftReason), t.get(S.voidedAt), t.get(S.endedAt),
                t.get(S.createdAt), t.get(A.id), t.get(A.appealNo), t.get(A.status), t.get(P.nickname), t.get(P.gradeKey),
                userStatus == null ? null : userStatus.name(), Boolean.TRUE.equals(t.get(S.sealed)));
    }

    public Optional<SanctionRow> find(long id) {
        return Optional.ofNullable(sanctionRows().where(S.id.eq(id)).fetchOne()).map(MemberSanctionStore::row);
    }

    /** 그 제재의 회원(없으면 빈 값) */
    public Optional<Long> userOf(long sanctionId) {
        return Optional.ofNullable(query.select(S.userId).from(S).where(S.id.eq(sanctionId)).fetchOne());
    }

    /** 그 회원의 제재 — 최근 것이 위, 최대 {@code limit}(무효 포함). 색인 {@code ix_member_sanctions_user} */
    public List<SanctionRow> ofUser(long userId, int limit) {
        return sanctionRows().where(S.userId.eq(userId)).orderBy(S.createdAt.desc(), S.id.desc()).limit(limit).fetch().stream().map(MemberSanctionStore::row).toList();
    }

    /** 그 회원의 모든 제재(단계 계산용 — 회원 하나의 제재 수는 적다) */
    public List<SanctionRow> allOfUser(long userId) {
        return sanctionRows().where(S.userId.eq(userId)).orderBy(S.createdAt.desc(), S.id.desc()).fetch().stream().map(MemberSanctionStore::row).toList();
    }

    /** 새 제재 — 번호는 DB 시퀀스가 매긴다(넣고 내보낸 뒤 읽어 돌려준다) */
    public long insert(long id, long userId, SanctionKind kind, Integer warningNo, boolean skipped, String reason, Instant startsAt, Instant endsAt, long issuedBy,
                       Long reportCaseId, Instant now) {
        return insert(id, userId, kind, warningNo, skipped, reason, startsAt, endsAt, issuedBy, reportCaseId, now, false);
    }

    /** @param sealed 익명 글·댓글 작성자로 건 제재(V184 — 봉인, anonymous-board-contract 정책 9) */
    public long insert(long id, long userId, SanctionKind kind, Integer warningNo, boolean skipped, String reason, Instant startsAt, Instant endsAt, long issuedBy,
                       Long reportCaseId, Instant now, boolean sealed) {
        MemberSanctionEntity sanction = MemberSanctionEntity.issue(id, userId, kind, warningNo, skipped, reason, startsAt, endsAt, issuedBy, reportCaseId, now);
        if (sealed) {
            sanction.seal();
        }
        sanctions.persist(sanction);
        sanctions.flush();
        return sanction.getSanctionNo() == null ? 0 : sanction.getSanctionNo();
    }

    /** 진행 중인 정지를 영구 정지로 대체해 끝낸다(ended_at = 지금). 끝낸 제재 ID(없으면 빈 값) — 회원 행을 잠근 뒤(진행 중 정지는 회원마다 하나) */
    public Optional<Long> endRunning(long userId, Instant now) {
        Long running = query.select(S.id).from(S).where(S.userId.eq(userId), runningStored(S)).fetchFirst();
        if (running == null) {
            return Optional.empty();
        }
        long ended = query.update(S).set(S.status, SanctionStatus.ENDED).set(S.endedAt, now).set(S.updatedAt, now)
                .where(S.id.eq(running), runningStored(S)).execute();
        return ended == 1 ? Optional.of(running) : Optional.empty();
    }

    /** 진행 중인 정지·영구 정지를 푼다(조건부 — 끝이 지났거나 이미 끝났으면 바꾸지 않는다). 풀었으면 true */
    public boolean lift(long id, long operatorId, String reason, Instant now) {
        return query.update(S).set(S.status, SanctionStatus.LIFTED).set(S.liftedBy, operatorId).set(S.liftedAt, now).set(S.liftReason, reason).set(S.updatedAt, now)
                .where(S.id.eq(id), runningStored(S), S.endsAt.isNull().or(S.endsAt.gt(now))).execute() == 1;
    }

    /** 이의제기 인용 — 무효로(어느 상태에서든). 바꿨으면 true */
    public boolean voidSanction(long id, Instant now) {
        return query.update(S).set(S.status, SanctionStatus.VOIDED).set(S.voidedAt, now).setNull(S.warningNo).set(S.updatedAt, now)
                .where(S.id.eq(id), S.status.ne(SanctionStatus.VOIDED)).execute() == 1;
    }

    /**
     * 유효한 경고의 번호를 받은 차례대로 다시 매긴다(1·2 — 2 넘으면 2) — 경고가 무효가 된 뒤.
     *
     * <p>Native SQL 인 이유: 윈도 함수({@code row_number() OVER}) 결과로 고치는 {@code UPDATE … FROM} 한 문장 — JPQL·QueryDSL 에 대응 문법이 없다.
     */
    public void renumberWarnings(long userId, Instant now) {
        entityManager.createNativeQuery("UPDATE member_sanctions m SET warning_no = t.n, updated_at = ?1 FROM (SELECT id, least(row_number() OVER (ORDER BY created_at, id), 2) AS n "
                        + "FROM member_sanctions WHERE user_id = ?2 AND kind = 'WARNING' AND status <> 'VOIDED') t WHERE m.id = t.id AND m.warning_no IS DISTINCT FROM t.n")
                .setParameter(1, ts(now)).setParameter(2, userId).executeUpdate();
    }

    /** 경고 확인(멱등) — 내 제재가 아니면 false */
    public boolean acknowledge(long id, long userId, Instant now) {
        if (query.selectOne().from(S).where(S.id.eq(id), S.userId.eq(userId)).fetchFirst() == null) {
            return false;
        }
        query.update(S).set(S.acknowledgedAt, now).set(S.updatedAt, now)
                .where(S.id.eq(id), S.userId.eq(userId), S.kind.eq(SanctionKind.WARNING), S.acknowledgedAt.isNull()).execute();
        return true;
    }

    /** 확인하지 않은 유효한 경고 수 */
    public int pendingWarnings(long userId) {
        Long n = query.select(S.count()).from(S)
                .where(S.userId.eq(userId), S.kind.eq(SanctionKind.WARNING), S.status.eq(SanctionStatus.ACTIVE), S.acknowledgedAt.isNull()).fetchOne();
        return n == null ? 0 : n.intValue();
    }

    /** 진행 중인 기간 정지의 끝(영구 정지·정지 없음이면 빈 값) */
    public Optional<Instant> runningUntil(long userId, Instant now) {
        return Optional.ofNullable(query.select(S.endsAt).from(S).where(S.userId.eq(userId), runningStored(S), S.endsAt.gt(now)).fetchFirst());
    }

    // ── 이의제기 ──────────────────────────────────────────────────────────

    /**
     * 이의제기 접수 — 그 제재에 이미 있으면 넣지 않는다(유일 색인). 넣은 이의제기 번호(없으면 빈 값).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING RETURNING} — 동시 접수를 유일 색인이 한 번만 받게 하고 번호를 같은 문장에서 받는다.
     */
    public Optional<Long> insertAppeal(long id, long sanctionId, long userId, String reason, Instant now) {
        return firstLong(entityManager.createNativeQuery("INSERT INTO sanction_appeals (id, sanction_id, user_id, status, reason, created_at, updated_at) "
                        + "VALUES (?1, ?2, ?3, 'PENDING', ?4, ?5, ?5) ON CONFLICT (sanction_id) DO NOTHING RETURNING appeal_no")
                .setParameter(1, id).setParameter(2, sanctionId).setParameter(3, userId).setParameter(4, reason).setParameter(5, ts(now)).getResultList());
    }

    /** 이의제기 한 줄 질의(답변한 운영자 이름을 함께) — 거르기·차례는 부르는 쪽이 붙인다 */
    public JPAQuery<Tuple> appealRows() {
        return query.select(A.id, A.appealNo, A.sanctionId, A.userId, A.status, A.reason, A.answer, A.answeredBy, ANSWERED.operatorName, A.answeredAt, A.createdAt,
                A.boardSanctionId).from(A).leftJoin(ANSWERED).on(ANSWERED.id.eq(A.answeredBy));
    }

    public static AppealRow appeal(Tuple t) {
        return new AppealRow(t.get(A.id), t.get(A.appealNo), t.get(A.sanctionId), t.get(A.userId), t.get(A.status), t.get(A.reason), t.get(A.answer),
                t.get(A.answeredBy), t.get(ANSWERED.operatorName), t.get(A.answeredAt), t.get(A.createdAt), t.get(A.boardSanctionId));
    }

    public Optional<AppealRow> findAppeal(long id) {
        return Optional.ofNullable(appealRows().where(A.id.eq(id)).fetchOne()).map(MemberSanctionStore::appeal);
    }

    /** 제재의 이의제기 */
    public Optional<AppealRow> appealOf(long sanctionId) {
        return Optional.ofNullable(appealRows().where(A.sanctionId.eq(sanctionId)).fetchOne()).map(MemberSanctionStore::appeal);
    }

    /** 회원의 이의제기 전부(내 제재 화면 — 제재 ID 로 잇는다) */
    public List<AppealRow> appealsOfUser(long userId) {
        return appealRows().where(A.userId.eq(userId), A.sanctionId.isNotNull()).fetch().stream().map(MemberSanctionStore::appeal).toList();
    }

    /**
     * 이의제기 행을 쓰기 잠금으로 읽는다 — 두 운영자가 동시에 답해도 한 번만(차례: 이의제기 행 → 회원 행 → 제재 행). 답한 운영자 이름은 비운다. 이의제기 표만 고르는 한 문장
     * ({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다).
     */
    public Optional<AppealRow> lockAppeal(long id) {
        Tuple t = query.select(A.id, A.appealNo, A.sanctionId, A.userId, A.status, A.reason, A.answer, A.answeredBy, A.answeredAt, A.createdAt, A.boardSanctionId)
                .from(A).where(A.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
        return t == null ? Optional.empty()
                : Optional.of(new AppealRow(t.get(A.id), t.get(A.appealNo), t.get(A.sanctionId), t.get(A.userId), t.get(A.status), t.get(A.reason), t.get(A.answer),
                t.get(A.answeredBy), null, t.get(A.answeredAt), t.get(A.createdAt), t.get(A.boardSanctionId)));
    }

    /** 답변(조건부 — 대기일 때만). 바꿨으면 true */
    public boolean answerAppeal(long id, AppealStatus to, String answer, long operatorId, Instant now) {
        return query.update(A).set(A.status, to).set(A.answer, answer).set(A.answeredBy, operatorId).set(A.answeredAt, now).set(A.updatedAt, now)
                .where(A.id.eq(id), A.status.eq(AppealStatus.PENDING)).execute() == 1;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private static MemberRow member(Tuple t) {
        UserStatus status = t.get(U.status);
        boolean gone = status == UserStatus.WITHDRAWN || status == UserStatus.WITHDRAWING || t.get(U.deletedAt) != null;
        return new MemberRow(t.get(U.id), status == null ? null : status.name(), gone, t.get(P.nickname), t.get(P.gradeKey));
    }

    /** native {@code RETURNING} 한 칸 결과의 첫 값 */
    static Optional<Long> firstLong(List<?> rows) {
        return rows.stream().findFirst().map(v -> ((Number) v).longValue());
    }

    /** native 인자 — {@code timestamptz} 로 보낸다 */
    public static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    // ── JDBC 도우미(다른 업무의 JDBC 저장소가 함께 쓴다 — OperatorMemberAccountStore) ──────────

    public static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
