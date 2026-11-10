package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.MemberSanctionStore.firstLong;
import static com.community.sanction.repository.master.MemberSanctionStore.ts;

import com.community.board.dto.BoardWriteRestriction;
import com.community.board.entity.QBoardDefaultsEntity;
import com.community.board.entity.QBoardEntity;
import com.community.board.entity.QBoardStaffEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.permission.entity.MemberPermission;
import com.community.post.entity.QPostEntity;
import com.community.role.entity.MemberRoleIds;
import com.community.role.entity.QRoleEntity;
import com.community.role.entity.QRolePermissionEntity;
import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.BoardSanctionEntity;
import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.entity.BoardSanctionReviewEntity;
import com.community.sanction.entity.BoardStaffActionEntity;
import com.community.sanction.entity.QBoardSanctionEntity;
import com.community.sanction.entity.QBoardSanctionReviewEntity;
import com.community.sanction.entity.QBoardStaffUsageEntity;
import com.community.sanction.entity.QMemberSanctionEntity;
import com.community.sanction.entity.QSanctionAppealEntity;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.entity.SanctionStatus;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 게시판 제재({@code board_sanctions}) · 매니저·스텝 하루 사용량({@code board_staff_usage}) · 처리 기록({@code board_staff_actions}) · 반복 검토({@code board_sanction_reviews}) ·
 * 게시판 제재 이의제기({@code sanction_appeals.board_sanction_id}) 읽기·쓰기 — shared V137. 서비스({@code BoardSanctionService})와 api·관리자 목록 저장소가 부른다.
 * 판정 시각은 DB 의 {@code now()} 가 아니라 서비스가 넘기는 지금이다.
 *
 * <p>QueryDSL 이 기본이다 — 대상 회원 행 쓰기 잠금 아래의 조건부 대량 UPDATE(해제 · 무효 · 만료 — 한 번만 이긴다), 진행 중 쓰기 제한의 판정을 부분 색인
 * ({@code ux_board_sanctions_running} · {@code ix_board_sanctions_expire})과 같은 조건으로 둔다. 읽기는 한 줄 모양(프로젝션)이다.
 * Native SQL 로 남긴 것과 이유: 하루 한도의 원자적 증가({@code INSERT … ON CONFLICT … DO UPDATE … WHERE sanctions < 한도 RETURNING}), 이의제기·반복 검토 열기의
 * {@code ON CONFLICT DO NOTHING RETURNING} — 서버 여러 대가 동시에 불러도 유일 색인이 한 번만 받게 하고 결과를 같은 문장에서 받는다.
 *
 * <p>⚠ {@link #endDue} 는 DB 배치 {@code R__batch_40_board_sanction_expire.sql} 규칙 1 과 같은 판정이다(이중 구현) — 한쪽을 고치면 다른 쪽도 고친다.
 */
@Repository
public class BoardSanctionStore extends RoboyamRepositoryQDSL {

    /** 게시판 제재 — 한 줄 모양의 별칭(관리자 목록 저장소가 거르기에 쓴다) */
    public static final QBoardSanctionEntity S = QBoardSanctionEntity.boardSanctionEntity;
    /** 게시판 */
    public static final QBoardEntity B = QBoardEntity.boardEntity;
    /** 대상 회원의 프로필(닉네임) */
    public static final QProfileEntity P = QProfileEntity.profileEntity;
    /** 반복 검토 */
    public static final QBoardSanctionReviewEntity R = QBoardSanctionReviewEntity.boardSanctionReviewEntity;

    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity ISSUER = new QProfileEntity("issuerProfile");
    private static final QProfileEntity LIFTER = new QProfileEntity("lifterProfile");
    private static final QOperatorEntity LIFTED_OPERATOR = new QOperatorEntity("liftedOperator");
    private static final QOperatorEntity VOIDED_OPERATOR = new QOperatorEntity("voidedOperator");
    private static final QOperatorEntity ISSUED_OPERATOR = new QOperatorEntity("issuedOperator");
    private static final QOperatorEntity CLOSED_OPERATOR = new QOperatorEntity("closedOperator");
    private static final QSanctionAppealEntity A = new QSanctionAppealEntity("boardAppeal");
    private static final QMemberSanctionEntity SITE = new QMemberSanctionEntity("siteSanction");
    private static final QBoardStaffEntity STAFF = QBoardStaffEntity.boardStaffEntity;
    private static final QBoardStaffUsageEntity USAGE = QBoardStaffUsageEntity.boardStaffUsageEntity;
    private static final QBoardDefaultsEntity DEFAULTS = QBoardDefaultsEntity.boardDefaultsEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;
    private static final QRoleEntity ROLE = QRoleEntity.roleEntity;
    private static final QRolePermissionEntity ROLE_PERMISSION = QRolePermissionEntity.rolePermissionEntity;

    /** 반복 검토 기준 기간 — 최근 7일(롤링, 결정 3) */
    public static final java.time.Duration REVIEW_WINDOW = java.time.Duration.ofDays(7);

    /** 준 사람 자리 — 운영자 회원 계정(V141, operator-member-account-contract 정책 11) */
    public static final String ROLE_OPERATOR = "OPERATOR";

    /** 하루 제재 한도의 마지막 기본값(게시판 값 · board_defaults 가 없을 때) */
    private static final int DEFAULT_DAILY_LIMIT = 5;

    /**
     * 게시판 제재 한 줄.
     *
     * @param status        저장된 상태 — 끝이 지났지만 아직 정리되지 않은 쓰기 제한은 {@link #effectiveStatus} 가 ENDED 로 본다
     * @param issuedRole    줄 때의 자리(MANAGER · STAFF · OPERATOR — 운영자 회원 계정, {@code issuedBy} = 그 회원 · {@code issuedOperatorId} = 운영자)
     * @param liftedByType  MANAGER(그 게시판 매니저 — {@code liftedByUser}) · OPERATOR(운영자 — {@code liftedByOperator})
     * @param voidSource    APPEAL · OPERATOR · REVIEW
     * @param sealed        익명 글·댓글 작성자로 건 제재(V184) — 화면에는 대상 닉네임·회원 ID 대신 봉인(anonymous-board-contract 정책 9)
     */
    public record BoardSanctionRow(long id, long no, long boardId, String boardSlug, String boardName, String boardNameEn, long userId, String nickname,
                                   String gradeKey, String userStatus, BoardSanctionKind kind, SanctionStatus status, String reasonCode, String reasonText,
                                   Long issuedBy, String issuedByNickname, String issuedRole, Long postId, Long commentId, String sourceTitle, Instant startsAt,
                                   Instant endsAt, Instant acknowledgedAt, Instant liftedAt, String liftedByType, Long liftedByUser, String liftedByUserNickname,
                                   Long liftedByOperator, String liftedByOperatorName, String liftReason, Instant voidedAt, String voidSource, Long voidedBy,
                                   String voidedByName, String voidReason, Instant endedAt, Instant createdAt, Long appealId, Long appealNo, AppealStatus appealStatus,
                                   Long issuedOperatorId, String issuedOperatorName, boolean sealed) {

        /** 운영자 회원 계정이 준 제재인가 — 매니저는 풀지 못한다(operator-member-account-contract 정책 7) */
        public boolean byOperator() {
            return ROLE_OPERATOR.equals(issuedRole);
        }

        /** {@code now} 의 상태 — 진행 중인데 끝이 지난 쓰기 제한은 ENDED(크론이 1분 안에 그렇게 만든다) */
        public SanctionStatus effectiveStatus(Instant now) {
            return status == SanctionStatus.ACTIVE && due(now) ? SanctionStatus.ENDED : status;
        }

        /** 끝난 시각 — 아직 정리 전이면 끝 시각 */
        public Instant effectiveEndedAt(Instant now) {
            return endedAt == null && status == SanctionStatus.ACTIVE && due(now) ? endsAt : endedAt;
        }

        /** {@code now} 에 진행 중인 쓰기 제한인가 */
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
     * 반복 검토 한 줄.
     *
     * @param countedAfter 열 때의 마지막으로 닫힌 검토 closed_at(그 이전 제재는 세지 않는다) — 없으면 null
     */
    public record ReviewRow(long id, long no, long boardId, String boardSlug, String boardName, String boardNameEn, long userId, String nickname, String gradeKey,
                            String userStatus, String status, Instant openedAt, int openedCount, Instant countedAfter, Instant closedAt, Long closedBy,
                            String closedByName, String result, String memo, Long siteSanctionId, Long siteSanctionNo, String siteSanctionKind,
                            List<Long> voidedSanctionIds, Integer closedCount, Integer closedBoardCount) {

        public boolean open() {
            return "OPEN".equals(status);
        }

        /** 이 검토가 세는 제재의 시작 경계(이 시각 <b>뒤</b>에 받은 것) — 열 때 기준 7일 앞과 마지막으로 닫힌 검토 중 늦은 쪽 */
        public Instant windowStart() {
            Instant byWindow = openedAt.minus(REVIEW_WINDOW);
            return countedAfter != null && countedAfter.isAfter(byWindow) ? countedAfter : byWindow;
        }
    }

    /**
     * 매니저·스텝 판정 — 그 게시판에서 맡은 자리와 역할 권한, 계정 상태.
     *
     * @param role       MANAGER · STAFF — 그 게시판 매니저·스텝이 아니면 null
     * @param accountOk  계정 정상(ACTIVE · 지워지지 않음) · 이메일 인증
     * @param canIssue   역할 권한 {@code board.sanction.issue}
     * @param canRestore 역할 권한 {@code board.staff-action.restore}(매니저 전용)
     */
    public record StaffGate(String role, boolean accountOk, boolean canIssue, boolean canRestore) {

        public boolean manager() {
            return "MANAGER".equals(role);
        }

        /** 제재를 줄 수 있다 */
        public boolean issuer() {
            return role != null && accountOk && canIssue;
        }
    }

    /** 제재 대상 회원 — {@code gone} 탈퇴(진행 중 포함)·지워짐 */
    public record TargetRow(long id, String nickname, boolean gone) {
    }

    /** 계기 글 — 게시판·작성자·제목 */
    public record PostSource(long boardId, Long authorId, String title) {
    }

    /** 그 게시판 유효한 제재 수 — 경고 · 쓰기 제한(해제 포함, 무효 제외) */
    public record Counts(long warnings, long restrictions) {
        public long total() {
            return warnings + restrictions;
        }
    }

    /** 다른 게시판 요약 한 줄(반복 검토 상세) */
    public record BoardSummary(long boardId, String slug, String name, String nameEn, long recentCount, long total, Instant activeUntil) {
    }

    /** 아직 확인하지 않은 게시판 경고 한 줄(게시판 머리 안내) */
    public record PendingWarning(long id, String reasonCode, Instant issuedAt) {
    }

    private final MemberSanctionStore sanctions;
    private final BoardSanctionRepository boardSanctions;
    private final BoardStaffActionRepository staffActions;
    private final EntityManager entityManager;

    public BoardSanctionStore(JPAQueryFactory query, MemberSanctionStore sanctions, BoardSanctionRepository boardSanctions, BoardStaffActionRepository staffActions,
                              EntityManager entityManager) {
        super(query);
        this.sanctions = sanctions;
        this.boardSanctions = boardSanctions;
        this.staffActions = staffActions;
        this.entityManager = entityManager;
    }

    // ── 조건 ──────────────────────────────────────────────────────────────

    /** 진행 중인 쓰기 제한(부분 유일 색인 {@code ux_board_sanctions_running} 과 같은 조건 + 끝이 지나지 않음) */
    public static BooleanExpression running(QBoardSanctionEntity s, Instant now) {
        return s.status.eq(SanctionStatus.ACTIVE).and(s.kind.ne(BoardSanctionKind.WARNING)).and(s.endsAt.gt(now));
    }

    /** 유효한(무효 아닌) 제재 */
    private static BooleanExpression valid(QBoardSanctionEntity s) {
        return s.status.ne(SanctionStatus.VOIDED);
    }

    /** 조건에 맞는 줄 수(합계 식 — 줄이 없으면 null) */
    public static NumberExpression<Long> countWhen(BooleanExpression condition) {
        return new CaseBuilder().when(condition).then(1L).otherwise(0L).sum();
    }

    /** 진행 중 쓰기 제한의 끝 중 가장 늦은 것(없으면 null) */
    public static com.querydsl.core.types.dsl.DateTimeExpression<Instant> runningUntilMax(QBoardSanctionEntity s, Instant now) {
        return new CaseBuilder().when(running(s, now)).then(s.endsAt).otherwise(Expressions.nullExpression(Instant.class)).max();
    }

    // ── 매니저·스텝 · 대상 ─────────────────────────────────────────────────

    /**
     * 그 게시판에서 이 회원의 자리·역할 권한. 역할 표에 그 역할이 아직 없으면(관리자 서버가 내장 역할을 시드하기 전) 권한을 제한하지 않는다({@code MemberRoleQueries} 와 같다).
     * 회원이 없으면 자리 없음.
     */
    public StaffGate gate(long boardId, long userId) {
        // 예전과 같이 한 문장 — 자리·계정·역할 시드 여부·역할 권한을 같은 시점에 읽는다(두 매니저·스텝 역할과 그 권한을 함께 이어 읽고 자바가 자기 역할 것만 고른다)
        List<Tuple> rows = query.select(STAFF.role, U.status, U.deletedAt, U.emailVerifiedAt, ROLE.roleId, ROLE_PERMISSION.permissionCode).from(U)
                .leftJoin(STAFF).on(STAFF.userId.eq(U.id), STAFF.boardId.eq(boardId))
                .leftJoin(ROLE).on(STAFF.role.isNotNull(), ROLE.roleId.in(MemberRoleIds.BOARD_MANAGER, MemberRoleIds.BOARD_STAFF))
                .leftJoin(ROLE_PERMISSION).on(ROLE_PERMISSION.roleId.eq(ROLE.roleId))
                .where(U.id.eq(userId)).fetch();
        if (rows.isEmpty()) {
            return new StaffGate(null, false, false, false);
        }
        Tuple t = rows.get(0);
        String role = t.get(STAFF.role);
        boolean accountOk = t.get(U.status) == UserStatus.ACTIVE && t.get(U.deletedAt) == null && t.get(U.emailVerifiedAt) != null;
        if (role == null) {
            return new StaffGate(null, accountOk, false, false);
        }
        String roleId = "MANAGER".equals(role) ? MemberRoleIds.BOARD_MANAGER : MemberRoleIds.BOARD_STAFF;
        boolean seeded = rows.stream().anyMatch(r -> roleId.equals(r.get(ROLE.roleId)));
        Set<String> codes = rows.stream().filter(r -> roleId.equals(r.get(ROLE.roleId)) && r.get(ROLE_PERMISSION.permissionCode) != null)
                .map(r -> r.get(ROLE_PERMISSION.permissionCode)).collect(Collectors.toUnmodifiableSet());
        boolean canIssue = !seeded || codes.contains(MemberPermission.BOARD_SANCTION_ISSUE.code());
        boolean canRestore = "MANAGER".equals(role) && (!seeded || codes.contains(MemberPermission.BOARD_STAFF_ACTION_RESTORE.code()));
        return new StaffGate(role, accountOk, canIssue, canRestore);
    }

    /** 게시판 종류(GENERAL · DEAL · PROMO · GROUP) — 없으면 빈 값 */
    public Optional<String> boardKind(long boardId) {
        return Optional.ofNullable(query.select(B.kind).from(B).where(B.id.eq(boardId)).fetchOne());
    }

    /** 그 게시판의 매니저·스텝인가(대상 보호) */
    public boolean staffOf(long boardId, long userId) {
        return query.selectOne().from(STAFF).where(STAFF.boardId.eq(boardId), STAFF.userId.eq(userId)).fetchFirst() != null;
    }

    public Optional<TargetRow> target(long userId) {
        return Optional.ofNullable(targetQuery(userId).fetchOne()).map(BoardSanctionStore::target);
    }

    /**
     * 대상 회원 행을 쓰기 잠금으로 잡고({@code FOR NO KEY UPDATE}) 읽는다 — 같은 회원의 부여·해제·무효·이의 인용이 줄을 선다(사이트 제재와 같은 잠금,
     * 차례: 이의제기 행 → 회원 행 → 제재 행). 잠금은 회원 표만 고르는 한 문장으로 잡는다({@link MemberSanctionStore#lockMember} 와 같은 까닭).
     */
    public Optional<TargetRow> lockTarget(long userId) {
        if (query.select(U.id).from(U).where(U.id.eq(userId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() == null) {
            return Optional.empty();
        }
        return target(userId);
    }

    private JPAQuery<Tuple> targetQuery(long userId) {
        return query.select(U.id, P.nickname, U.status, U.deletedAt).from(U).leftJoin(P).on(P.userId.eq(U.id)).where(U.id.eq(userId));
    }

    /** 계기 글(지워진 글도 — 제목 스냅숏) */
    public Optional<PostSource> post(long postId) {
        Tuple t = query.select(POST.boardId, POST.authorId, POST.title).from(POST).where(POST.id.eq(postId)).fetchOne();
        return t == null ? Optional.empty() : Optional.of(new PostSource(t.get(POST.boardId), t.get(POST.authorId), t.get(POST.title)));
    }

    /** 계기 댓글의 게시판·작성자 — 그 글의 댓글만(파티션 키 post_id) */
    public Optional<PostSource> comment(long postId, long commentId) {
        Tuple t = query.select(COMMENT.boardId, COMMENT.authorId).from(COMMENT).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId)).fetchOne();
        return t == null ? Optional.empty() : Optional.of(new PostSource(t.get(COMMENT.boardId), t.get(COMMENT.authorId), null));
    }

    // ── 하루 한도 ─────────────────────────────────────────────────────────

    /** 그 게시판의 매니저·스텝 하루 제재 한도 — 게시판 값 ?? 기본값(board_defaults, 가장 앞 줄) ?? 5 */
    public int dailyLimit(long boardId) {
        Integer own = query.select(B.staffSanctionDailyLimit).from(B).where(B.id.eq(boardId)).fetchOne();
        if (own != null) {
            return own;
        }
        Integer fallback = query.select(DEFAULTS.staffSanctionDailyLimit).from(DEFAULTS).orderBy(DEFAULTS.id.asc()).fetchFirst();
        return fallback == null ? DEFAULT_DAILY_LIMIT : fallback;
    }

    /** 그 날 쓴 제재 수 */
    public int used(long boardId, long userId, LocalDate date) {
        Integer n = query.select(USAGE.sanctions).from(USAGE).where(USAGE.boardId.eq(boardId), USAGE.userId.eq(userId), USAGE.usageDate.eq(date)).fetchOne();
        return n == null ? 0 : n;
    }

    /**
     * 한도 안에서 하나 쓴다(원자적 증가 — 서버 여러 대가 동시에 불러도 한도를 넘지 않는다). 쓴 뒤의 사용량, 한도를 다 썼으면 빈 값(행을 바꾸지 않는다).
     * 트랜잭션이 롤백되면 차감도 되돌아간다.
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT … DO UPDATE … WHERE sanctions < 한도 RETURNING} — 없으면 만들기·한도 안 증가·결과를 한 문장에서 한다(나누면 동시 요청이 한도를 넘는다).
     */
    public Optional<Integer> consume(long boardId, long userId, LocalDate date, int limit, Instant now) {
        return firstLong(entityManager.createNativeQuery("INSERT INTO board_staff_usage (board_id, user_id, usage_date, sanctions, updated_at) VALUES (?1, ?2, ?3, 1, ?4) "
                        + "ON CONFLICT (board_id, user_id, usage_date) DO UPDATE SET sanctions = board_staff_usage.sanctions + 1, updated_at = EXCLUDED.updated_at "
                        + "WHERE board_staff_usage.sanctions < ?5 RETURNING sanctions")
                .setParameter(1, boardId).setParameter(2, userId).setParameter(3, date).setParameter(4, ts(now)).setParameter(5, limit).getResultList())
                .map(Long::intValue);
    }

    // ── 제재 읽기 ─────────────────────────────────────────────────────────

    /**
     * 게시판 제재 한 줄 질의 — 게시판·회원(닉네임·등급·상태)·처리자 닉네임·해제한 매니저 닉네임/운영자 이름·무효로 한 운영자 이름·이의제기 요약을 함께. 거르기·차례는 부르는 쪽이 붙인다.
     */
    public JPAQuery<Tuple> sanctionRows() {
        return query.select(S.id, S.sanctionNo, S.boardId, B.slug, B.name, B.nameEn, S.userId, P.nickname, P.gradeKey, U.status,
                        S.kind, S.status, S.reasonCode, S.reasonText, S.issuedBy, ISSUER.nickname, S.issuedRole, S.postId, S.commentId, S.sourceTitle,
                        S.startsAt, S.endsAt, S.acknowledgedAt, S.liftedAt, S.liftedByType, S.liftedByUser, LIFTER.nickname, S.liftedByOperator,
                        LIFTED_OPERATOR.operatorName, S.liftReason, S.voidedAt, S.voidSource, S.voidedBy, VOIDED_OPERATOR.operatorName, S.voidReason, S.endedAt,
                        S.createdAt, A.id, A.appealNo, A.status, S.issuedOperatorId, ISSUED_OPERATOR.operatorName, S.sealed)
                .from(S).join(B).on(B.id.eq(S.boardId)).join(U).on(U.id.eq(S.userId)).leftJoin(P).on(P.userId.eq(S.userId))
                .leftJoin(ISSUER).on(ISSUER.userId.eq(S.issuedBy)).leftJoin(LIFTER).on(LIFTER.userId.eq(S.liftedByUser))
                .leftJoin(LIFTED_OPERATOR).on(LIFTED_OPERATOR.id.eq(S.liftedByOperator)).leftJoin(VOIDED_OPERATOR).on(VOIDED_OPERATOR.id.eq(S.voidedBy))
                .leftJoin(ISSUED_OPERATOR).on(ISSUED_OPERATOR.id.eq(S.issuedOperatorId))
                .leftJoin(A).on(A.boardSanctionId.eq(S.id));
    }

    public static BoardSanctionRow row(Tuple t) {
        UserStatus userStatus = t.get(U.status);
        return new BoardSanctionRow(t.get(S.id), t.get(S.sanctionNo), t.get(S.boardId), t.get(B.slug), t.get(B.name), t.get(B.nameEn), t.get(S.userId),
                t.get(P.nickname), t.get(P.gradeKey), userStatus == null ? null : userStatus.name(), t.get(S.kind), t.get(S.status), t.get(S.reasonCode),
                t.get(S.reasonText), t.get(S.issuedBy), t.get(ISSUER.nickname), t.get(S.issuedRole), t.get(S.postId), t.get(S.commentId), t.get(S.sourceTitle),
                t.get(S.startsAt), t.get(S.endsAt), t.get(S.acknowledgedAt), t.get(S.liftedAt), t.get(S.liftedByType), t.get(S.liftedByUser), t.get(LIFTER.nickname),
                t.get(S.liftedByOperator), t.get(LIFTED_OPERATOR.operatorName), t.get(S.liftReason), t.get(S.voidedAt), t.get(S.voidSource), t.get(S.voidedBy),
                t.get(VOIDED_OPERATOR.operatorName), t.get(S.voidReason), t.get(S.endedAt), t.get(S.createdAt), t.get(A.id), t.get(A.appealNo), t.get(A.status),
                t.get(S.issuedOperatorId), t.get(ISSUED_OPERATOR.operatorName), Boolean.TRUE.equals(t.get(S.sealed)));
    }

    private static List<BoardSanctionRow> rows(List<Tuple> tuples) {
        return tuples.stream().map(BoardSanctionStore::row).toList();
    }

    public Optional<BoardSanctionRow> find(long id) {
        return Optional.ofNullable(sanctionRows().where(S.id.eq(id)).fetchOne()).map(BoardSanctionStore::row);
    }

    /** 여럿(ID 로) */
    public Map<Long, BoardSanctionRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return rows(sanctionRows().where(S.id.in(ids)).fetch()).stream().collect(Collectors.toMap(BoardSanctionRow::id, r -> r));
    }

    /** 그 제재의 회원·게시판(없으면 빈 값) — {@code [userId, boardId]} */
    public Optional<long[]> ownerOf(long id) {
        Tuple t = query.select(S.userId, S.boardId).from(S).where(S.id.eq(id)).fetchOne();
        return t == null ? Optional.empty() : Optional.of(new long[]{t.get(S.userId), t.get(S.boardId)});
    }

    /** 그 회원의 게시판 제재 — 최근 것이 위, 최대 {@code limit}(무효 포함, 모든 게시판). 색인 {@code ix_board_sanctions_user} */
    public List<BoardSanctionRow> ofUser(long userId, int limit) {
        return rows(sanctionRows().where(S.userId.eq(userId)).orderBy(S.createdAt.desc(), S.id.desc()).limit(limit).fetch());
    }

    /** 그 회원·그 게시판의 제재 — 최근 것이 위, 최대 {@code limit} */
    public List<BoardSanctionRow> ofUserBoard(long userId, long boardId, int limit) {
        return rows(sanctionRows().where(S.userId.eq(userId), S.boardId.eq(boardId)).orderBy(S.createdAt.desc(), S.id.desc()).limit(limit).fetch());
    }

    /** 그 회원·그 게시판에서 {@code after} 뒤에(없으면 처음부터) 받은 제재 — 받은 차례(반복 검토가 센 것) */
    public List<BoardSanctionRow> ofUserBoardBetween(long userId, long boardId, Instant after, Instant until) {
        return rows(sanctionRows().where(S.userId.eq(userId), S.boardId.eq(boardId), S.createdAt.gt(after), S.createdAt.loe(until))
                .orderBy(S.createdAt.asc(), S.id.asc()).fetch());
    }

    /**
     * 그 회원·그 게시판에서 {@code after} 뒤 ~ {@code until} 까지 받은 <b>봉인</b> 제재(V184 — 「이 글/댓글 작성자」) 가운데 가장 최근 것의 계기 글·댓글(무효 포함 — 반복 검토가 센
     * 범위와 같다). 없으면 빈 값. 회원 범위 색인.
     */
    public Optional<SealedSource> latestSealedBetween(long userId, long boardId, Instant after, Instant until) {
        Tuple t = query.select(S.postId, S.commentId).from(S)
                .where(S.userId.eq(userId), S.boardId.eq(boardId), S.sealed.isTrue(), S.createdAt.gt(after), S.createdAt.loe(until))
                .orderBy(S.createdAt.desc(), S.id.desc()).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new SealedSource(t.get(S.postId), t.get(S.commentId)));
    }

    /** 봉인 제재의 계기 — 댓글이면 {@code commentId} 가 있고 {@code postId} 는 그 댓글의 글(계기를 잃었으면 둘 다 null) */
    public record SealedSource(Long postId, Long commentId) {
    }

    /** 그 게시판의 유효한 제재 수(권장 단계 · 현황) */
    public Counts counts(long userId, long boardId) {
        NumberExpression<Long> warnings = countWhen(S.kind.eq(BoardSanctionKind.WARNING));
        NumberExpression<Long> restrictions = countWhen(S.kind.ne(BoardSanctionKind.WARNING));
        Tuple t = query.select(warnings, restrictions).from(S).where(S.userId.eq(userId), S.boardId.eq(boardId), valid(S)).fetchOne();
        return new Counts(zero(t == null ? null : t.get(warnings)), zero(t == null ? null : t.get(restrictions)));
    }

    /** 그 게시판에서 진행 중인 쓰기 제한(없으면 빈 값) */
    public Optional<BoardWriteRestriction> running(long userId, long boardId, Instant now) {
        Tuple t = query.select(S.id, S.endsAt, S.reasonCode).from(S).where(S.userId.eq(userId), S.boardId.eq(boardId), running(S, now)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new BoardWriteRestriction(t.get(S.id), t.get(S.endsAt), t.get(S.reasonCode)));
    }

    /** 이 회원의 진행 중인 쓰기 제한 전부(게시판 ID → 제한) — 글·댓글 쓰기 판정, 요청당 색인 질의 하나({@code ux_board_sanctions_running}) */
    public Map<Long, BoardWriteRestriction> runningOfUser(long userId, Instant now) {
        Map<Long, BoardWriteRestriction> out = new HashMap<>();
        query.select(S.boardId, S.id, S.endsAt, S.reasonCode).from(S).where(S.userId.eq(userId), running(S, now)).fetch()
                .forEach(t -> out.put(t.get(S.boardId), new BoardWriteRestriction(t.get(S.id), t.get(S.endsAt), t.get(S.reasonCode))));
        return out;
    }

    /** 그 회원이 그 게시판에서 아직 확인하지 않은 경고 — 최근 것이 위, 최대 {@code limit}(무효 제외). 색인 {@code ix_board_sanctions_user} */
    public List<PendingWarning> pendingWarnings(long userId, long boardId, int limit) {
        return query.select(S.id, S.reasonCode, S.createdAt).from(S)
                .where(S.userId.eq(userId), S.boardId.eq(boardId), S.kind.eq(BoardSanctionKind.WARNING), valid(S), S.acknowledgedAt.isNull())
                .orderBy(S.createdAt.desc(), S.id.desc()).limit(limit).fetch().stream()
                .map(t -> new PendingWarning(t.get(S.id), t.get(S.reasonCode), t.get(S.createdAt))).toList();
    }

    // ── 제재 쓰기 ─────────────────────────────────────────────────────────

    /** 그 회원·그 게시판의 끝이 지난 진행 중 쓰기 제한을 끝낸다(ended_at = 끝) — R__batch_40 규칙 1 과 같은 판정. 회원 행을 잠근 뒤. 끝낸 수 */
    public int endDue(long userId, long boardId, Instant now) {
        return (int) query.update(S).set(S.status, SanctionStatus.ENDED).set(S.endedAt, S.endsAt).set(S.updatedAt, now)
                .where(S.userId.eq(userId), S.boardId.eq(boardId), S.status.eq(SanctionStatus.ACTIVE), S.endsAt.isNotNull(), S.endsAt.loe(now)).execute();
    }

    /**
     * 새 제재 — 번호는 DB 시퀀스가 매긴다(넣고 내보낸 뒤 읽어 돌려준다).
     *
     * @param issuedOperatorId 운영자 회원 계정이 주면 그 운영자({@code issuedRole} OPERATOR), 아니면 null
     */
    public long insert(long id, long boardId, long userId, BoardSanctionKind kind, String reasonCode, String reasonText, long issuedBy, String issuedRole,
                       Long issuedOperatorId, Long postId, Long commentId, String sourceTitle, Instant startsAt, Instant endsAt, Instant now) {
        return insert(id, boardId, userId, kind, reasonCode, reasonText, issuedBy, issuedRole, issuedOperatorId, postId, commentId, sourceTitle, startsAt, endsAt, now,
                false);
    }

    /** @param sealed 익명 글·댓글 작성자로 건 제재(V184 — 봉인) */
    public long insert(long id, long boardId, long userId, BoardSanctionKind kind, String reasonCode, String reasonText, long issuedBy, String issuedRole,
                       Long issuedOperatorId, Long postId, Long commentId, String sourceTitle, Instant startsAt, Instant endsAt, Instant now, boolean sealed) {
        BoardSanctionEntity sanction = BoardSanctionEntity.issue(id, boardId, userId, kind, reasonCode, reasonText, issuedBy, issuedRole, issuedOperatorId, postId,
                commentId, sourceTitle, startsAt, endsAt, now);
        if (sealed) {
            sanction.seal();
        }
        boardSanctions.persist(sanction);
        boardSanctions.flush();
        return sanction.getSanctionNo() == null ? 0 : sanction.getSanctionNo();
    }

    /** 매니저·스텝(·운영자 회원 계정 — {@code actorOperatorId}) 처리 기록 한 줄(kind SANCTION) — 그 자리에서 내보낸다 */
    public void insertAction(long id, long boardId, long actorId, String actorRole, Long actorOperatorId, String action, long targetUserId, long boardSanctionId,
                             Long postId, Long commentId, String reason, Instant now) {
        staffActions.persist(BoardStaffActionEntity.sanction(id, boardId, actorId, actorRole, actorOperatorId, action, targetUserId, boardSanctionId, postId, commentId,
                reason, now));
        staffActions.flush();
    }

    /** 진행 중 쓰기 제한 해제 — 매니저({@code byUser}) 또는 운영자({@code byOperator}). 조건부(끝이 지났거나 이미 끝났으면 바꾸지 않는다). 풀었으면 true */
    public boolean lift(long id, Long byUser, Long byOperator, String reason, Instant now) {
        return query.update(S).set(S.status, SanctionStatus.LIFTED).set(S.liftedAt, now).set(S.liftedByType, byUser != null ? "MANAGER" : "OPERATOR")
                .set(S.liftedByUser, byUser).set(S.liftedByOperator, byOperator).set(S.liftReason, reason).set(S.updatedAt, now)
                .where(S.id.eq(id), running(S, now)).execute() == 1;
    }

    /** 무효로(어느 상태에서든 — 진행 중 쓰기 제한은 그 순간 풀린다). 바꿨으면 true */
    public boolean voidSanction(long id, String source, Long operatorId, String reason, Instant now) {
        return query.update(S).set(S.status, SanctionStatus.VOIDED).set(S.voidedAt, now).set(S.voidSource, source).set(S.voidedBy, operatorId)
                .set(S.voidReason, reason).set(S.updatedAt, now).where(S.id.eq(id), valid(S)).execute() == 1;
    }

    /** 경고 확인(멱등) — 내 제재가 아니면 false */
    public boolean acknowledge(long id, long userId, Instant now) {
        if (query.selectOne().from(S).where(S.id.eq(id), S.userId.eq(userId)).fetchFirst() == null) {
            return false;
        }
        query.update(S).set(S.acknowledgedAt, now).set(S.updatedAt, now)
                .where(S.id.eq(id), S.userId.eq(userId), S.kind.eq(BoardSanctionKind.WARNING), S.acknowledgedAt.isNull()).execute();
        return true;
    }

    // ── 반복 검토 ─────────────────────────────────────────────────────────

    /** 그 (회원, 게시판)의 열린 검토가 있나 */
    public boolean hasOpenReview(long userId, long boardId) {
        return query.selectOne().from(R).where(R.userId.eq(userId), R.boardId.eq(boardId), R.status.eq(BoardSanctionReviewEntity.OPEN)).fetchFirst() != null;
    }

    /** 그 (회원, 게시판)의 마지막으로 닫힌 검토의 closed_at(없으면 빈 값) */
    public Optional<Instant> lastClosedAt(long userId, long boardId) {
        return Optional.ofNullable(query.select(R.closedAt.max()).from(R)
                .where(R.userId.eq(userId), R.boardId.eq(boardId), R.status.eq(BoardSanctionReviewEntity.CLOSED)).fetchOne());
    }

    /** 그 (회원, 게시판)에서 {@code after} 뒤에 받은 유효한(VOIDED 아닌) 제재 수 */
    public int countValidAfter(long userId, long boardId, Instant after) {
        return (int) zero(query.select(S.count()).from(S).where(S.userId.eq(userId), S.boardId.eq(boardId), valid(S), S.createdAt.gt(after)).fetchOne());
    }

    /** 그 회원이 {@code after} 뒤에 유효한 게시판 제재를 받은 게시판 수 */
    public int boardsValidAfter(long userId, Instant after) {
        return (int) zero(query.select(S.boardId.countDistinct()).from(S).where(S.userId.eq(userId), valid(S), S.createdAt.gt(after)).fetchOne());
    }

    /**
     * 검토를 연다 — 그 (회원, 게시판)에 이미 열린 건이 있으면 넣지 않는다(부분 유일 색인). 넣은 검토 번호(없으면 빈 값).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT (…) WHERE status = 'OPEN' DO NOTHING RETURNING} — 동시에 열어도 부분 유일 색인이 한 번만 받고 번호를 같은 문장에서 받는다.
     */
    public Optional<Long> openReview(long id, long boardId, long userId, int count, Instant countedAfter, Instant now) {
        return firstLong(entityManager.createNativeQuery("INSERT INTO board_sanction_reviews (id, board_id, user_id, status, opened_at, opened_count, counted_after, "
                        + "created_at, updated_at) VALUES (?1, ?2, ?3, 'OPEN', ?4, ?5, ?6, ?4, ?4) ON CONFLICT (user_id, board_id) WHERE status = 'OPEN' DO NOTHING "
                        + "RETURNING review_no")
                .setParameter(1, id).setParameter(2, boardId).setParameter(3, userId).setParameter(4, ts(now)).setParameter(5, count)
                .setParameter(6, ts(countedAfter)).getResultList());
    }

    /** 반복 검토 한 줄 질의 — 게시판·회원·닫은 운영자 이름·닫으며 부여한 사이트 제재(번호·단계)를 함께. 거르기·차례는 부르는 쪽이 붙인다 */
    public JPAQuery<Tuple> reviewRows() {
        return query.select(R.id, R.reviewNo, R.boardId, B.slug, B.name, B.nameEn, R.userId, P.nickname, P.gradeKey, U.status,
                        R.status, R.openedAt, R.openedCount, R.countedAfter, R.closedAt, R.closedBy, CLOSED_OPERATOR.operatorName, R.result, R.memo, R.siteSanctionId,
                        SITE.sanctionNo, SITE.kind, R.voidedSanctionIds, R.closedCount, R.closedBoardCount)
                .from(R).join(B).on(B.id.eq(R.boardId)).join(U).on(U.id.eq(R.userId)).leftJoin(P).on(P.userId.eq(R.userId))
                .leftJoin(CLOSED_OPERATOR).on(CLOSED_OPERATOR.id.eq(R.closedBy)).leftJoin(SITE).on(SITE.id.eq(R.siteSanctionId));
    }

    public static ReviewRow review(Tuple t) {
        Long[] voided = t.get(R.voidedSanctionIds);
        UserStatus userStatus = t.get(U.status);
        SanctionKind siteKind = t.get(SITE.kind);
        return new ReviewRow(t.get(R.id), t.get(R.reviewNo), t.get(R.boardId), t.get(B.slug), t.get(B.name), t.get(B.nameEn), t.get(R.userId), t.get(P.nickname),
                t.get(P.gradeKey), userStatus == null ? null : userStatus.name(), t.get(R.status), t.get(R.openedAt), t.get(R.openedCount), t.get(R.countedAfter),
                t.get(R.closedAt), t.get(R.closedBy), t.get(CLOSED_OPERATOR.operatorName), t.get(R.result), t.get(R.memo), t.get(R.siteSanctionId),
                t.get(SITE.sanctionNo), siteKind == null ? null : siteKind.name(), voided == null ? List.of() : List.of(voided),
                t.get(R.closedCount), t.get(R.closedBoardCount));
    }

    public Optional<ReviewRow> findReview(long id) {
        return Optional.ofNullable(reviewRows().where(R.id.eq(id)).fetchOne()).map(BoardSanctionStore::review);
    }

    /**
     * 검토 행을 쓰기 잠금으로 — 두 운영자가 동시에 닫아도 한 번만(차례: 검토 행 → 이의제기 행 → 회원 행 → 제재 행). 회원·게시판 {@code [userId, boardId, open(1/0)]}.
     * 검토 표만 고르는 한 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다).
     */
    public Optional<long[]> lockReview(long id) {
        Tuple t = query.select(R.userId, R.boardId, R.status).from(R).where(R.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
        return t == null ? Optional.empty()
                : Optional.of(new long[]{t.get(R.userId), t.get(R.boardId), BoardSanctionReviewEntity.OPEN.equals(t.get(R.status)) ? 1 : 0});
    }

    /** 닫기(조건부 — 열린 건만). 닫았으면 true */
    public boolean closeReview(long id, String result, String memo, Long siteSanctionId, List<Long> voidedIds, int closedCount, int closedBoardCount, long operatorId,
                               Instant now) {
        return query.update(R).set(R.status, BoardSanctionReviewEntity.CLOSED).set(R.closedAt, now).set(R.closedBy, operatorId).set(R.result, result)
                .set(R.memo, memo).set(R.siteSanctionId, siteSanctionId).set(R.voidedSanctionIds, voidedIds.toArray(Long[]::new)).set(R.closedCount, closedCount)
                .set(R.closedBoardCount, closedBoardCount).set(R.updatedAt, now)
                .where(R.id.eq(id), R.status.eq(BoardSanctionReviewEntity.OPEN)).execute() == 1;
    }

    /** 그 회원의 열린 검토 수(회원 상세) */
    public int openReviewsOf(long userId) {
        return (int) zero(query.select(R.count()).from(R).where(R.userId.eq(userId), R.status.eq(BoardSanctionReviewEntity.OPEN)).fetchOne());
    }

    /**
     * 그 회원의 게시판별 요약 — {@code since} 뒤 유효한 수 · 유효한 전체 수 · 진행 중 쓰기 제한의 끝. 게시판 이름 차례. 회원 범위 색인 한 번.
     */
    public List<BoardSummary> boardSummaries(long userId, Instant since, Instant now) {
        return boardSummaries(userId, since, now, false);
    }

    /** @param withoutSealed 봉인 제재(V184)를 빼고 센다 — 회원이 드러난 관리자 화면(봉인 아닌 반복 검토의 다른 게시판, anonymous-board-contract 2026-11-11) */
    public List<BoardSummary> boardSummaries(long userId, Instant since, Instant now, boolean withoutSealed) {
        NumberExpression<Long> recent = countWhen(valid(S).and(S.createdAt.gt(since)));
        NumberExpression<Long> total = countWhen(valid(S));
        var until = runningUntilMax(S, now);
        return query.select(S.boardId, B.slug, B.name, B.nameEn, recent, total, until).from(S).join(B).on(B.id.eq(S.boardId))
                .where(S.userId.eq(userId), withoutSealed ? S.sealed.isFalse() : null)
                .groupBy(S.boardId, B.slug, B.name, B.nameEn).orderBy(B.name.asc(), S.boardId.asc()).fetch().stream()
                .map(t -> new BoardSummary(t.get(S.boardId), t.get(B.slug), t.get(B.name), t.get(B.nameEn), zero(t.get(recent)), zero(t.get(total)), t.get(until)))
                .toList();
    }

    // ── 이의제기 ──────────────────────────────────────────────────────────

    /**
     * 게시판 제재 이의제기 접수 — 이미 있으면 넣지 않는다(유일 색인). 넣은 접수 번호(없으면 빈 값).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING RETURNING} — 동시 접수를 유일 색인이 한 번만 받게 하고 번호를 같은 문장에서 받는다.
     */
    public Optional<Long> insertAppeal(long id, long boardSanctionId, long userId, String reason, Instant now) {
        return firstLong(entityManager.createNativeQuery("INSERT INTO sanction_appeals (id, board_sanction_id, user_id, status, reason, created_at, updated_at) "
                        + "VALUES (?1, ?2, ?3, 'PENDING', ?4, ?5, ?5) ON CONFLICT (board_sanction_id) DO NOTHING RETURNING appeal_no")
                .setParameter(1, id).setParameter(2, boardSanctionId).setParameter(3, userId).setParameter(4, reason).setParameter(5, ts(now)).getResultList());
    }

    /** 그 게시판 제재의 대기 이의제기를 쓰기 잠금으로(없으면 빈 값) — 무효 처리가 같은 트랜잭션에서 인용으로 닫는다(결정 10) */
    public Optional<Long> lockPendingAppeal(long boardSanctionId) {
        // 조건 잠금(id 차례, PESSIMISTIC_WRITE — sanction_appeals 를 FK 로 가리키는 표가 없다) — PostgreSQL 이 잠근 뒤 조건을 다시 본다. 조건을 넣어 다시 읽는다
        List<Long> locked = query.select(A.id).from(A).where(A.boardSanctionId.eq(boardSanctionId), A.status.eq(AppealStatus.PENDING)).orderBy(A.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
        return locked.isEmpty() ? Optional.empty()
                : Optional.ofNullable(query.select(A.id).from(A).where(A.id.in(locked), A.boardSanctionId.eq(boardSanctionId), A.status.eq(AppealStatus.PENDING))
                        .orderBy(A.id.asc()).fetchFirst());
    }

    /** 회원의 게시판 제재 이의제기(내 제재 화면 — 게시판 제재 ID → 이의제기) */
    public Map<Long, AppealRow> appealsOfUser(long userId) {
        return sanctions.appealRows().where(MemberSanctionStore.A.userId.eq(userId), MemberSanctionStore.A.boardSanctionId.isNotNull()).fetch().stream()
                .map(MemberSanctionStore::appeal).collect(Collectors.toMap(AppealRow::boardSanctionId, a -> a, (a, b) -> a));
    }

    // ── 매니저·스텝 내역 ──────────────────────────────────────────────────

    /** 제재 내역 거르기 — {@code issuedBy} 가 있으면 그 처리자가 준 것만(스텝), {@code scope} ALL · ACTIVE(진행 중 쓰기 제한) · ENDED(그 밖) */
    public long staffTotal(long boardId, Long issuedBy, String scope, Instant now) {
        return zero(query.select(S.count()).from(S).where(staffWhere(boardId, issuedBy, scope, now)).fetchOne());
    }

    public List<BoardSanctionRow> staffPage(long boardId, Long issuedBy, String scope, Instant now, int offset, int size) {
        return rows(sanctionRows().where(staffWhere(boardId, issuedBy, scope, now)).orderBy(S.createdAt.desc(), S.id.desc()).offset(offset).limit(size).fetch());
    }

    private static BooleanExpression staffWhere(long boardId, Long issuedBy, String scope, Instant now) {
        BooleanExpression where = S.boardId.eq(boardId);
        if (issuedBy != null) {
            where = where.and(S.issuedBy.eq(issuedBy));
        }
        if ("ACTIVE".equals(scope)) {
            where = where.and(running(S, now));
        } else if ("ENDED".equals(scope)) {
            where = where.and(running(S, now).not())
                    .and(S.kind.eq(BoardSanctionKind.WARNING).and(S.status.eq(SanctionStatus.ACTIVE)).not());
        }
        return where;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private static TargetRow target(Tuple t) {
        UserStatus status = t.get(U.status);
        return new TargetRow(t.get(U.id), t.get(P.nickname), status == UserStatus.WITHDRAWN || status == UserStatus.WITHDRAWING || t.get(U.deletedAt) != null);
    }

    /** 합계 식이 비었으면(줄 없음) 0 */
    public static long zero(Long n) {
        return n == null ? 0 : n;
    }
}
