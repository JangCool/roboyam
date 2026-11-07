package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.BoardSanctionStore.zero;
import static com.community.sanction.repository.master.MemberSanctionStore.firstLong;
import static com.community.sanction.repository.master.MemberSanctionStore.ts;

import com.community.board.entity.QBoardDefaultsEntity;
import com.community.board.entity.QBoardEntity;
import com.community.board.entity.QBoardStaffEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.permission.entity.MemberPermission;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.role.entity.MemberRoleIds;
import com.community.role.entity.QRoleEntity;
import com.community.role.entity.QRolePermissionEntity;
import com.community.sanction.entity.BoardStaffActionEntity;
import com.community.sanction.entity.QBoardStaffActionEntity;
import com.community.sanction.entity.QBoardStaffUsageEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
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
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 게시판 스텝 처리 기록({@code board_staff_actions} 의 kind POST · COMMENT — V138) · 하루 사용량({@code board_staff_usage.posts|comments}) · 처리자 판정 읽기·쓰기.
 * 서비스({@code BoardStaffActionService}) · 운영자 글 조치 몸통({@code PostModerationActions} — 정책 7) · 관리자 목록 저장소가 부른다.
 *
 * <p>QueryDSL 이 기본이다 — 되돌림은 조건부 대량 UPDATE({@code restored_at IS NULL} 일 때만 한 번), 목록은 부분 색인과 같은 조건({@link #content}).
 * Native SQL 로 남긴 것과 이유: 하루 한도의 원자적 증가({@code INSERT … ON CONFLICT … DO UPDATE … WHERE 칸 < 한도 RETURNING} 한 문장 — 서버 여러 대가 동시에 불러도
 * 메모리 상태 없이 DB 가 막는다).
 */
@Repository
public class BoardStaffActionStore extends RoboyamRepositoryQDSL {

    public static final String KIND_POST = "POST";
    public static final String KIND_COMMENT = "COMMENT";
    public static final String ACTION_HIDE = "HIDE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String BY_MANAGER = "MANAGER";
    public static final String BY_OPERATOR = "OPERATOR";
    /** 처리자 자리 — 운영자 회원 계정(V141, operator-member-account-contract 정책 11) */
    public static final String ROLE_OPERATOR = "OPERATOR";

    /** 처리 기록 — 한 줄 모양의 별칭(관리자 목록 저장소가 거르기에 쓴다) */
    public static final QBoardStaffActionEntity ACTION = QBoardStaffActionEntity.boardStaffActionEntity;
    /** 처리자의 프로필(닉네임 거르기) */
    public static final QProfileEntity ACTOR = new QProfileEntity("actorProfile");

    private static final QBoardEntity B = QBoardEntity.boardEntity;
    private static final QOperatorEntity ACTOR_OPERATOR = new QOperatorEntity("actorOperator");
    private static final QProfileEntity TARGET = new QProfileEntity("targetProfile");
    private static final QProfileEntity RESTORER = new QProfileEntity("restorerProfile");
    private static final QOperatorEntity RESTORER_OPERATOR = new QOperatorEntity("restorerOperator");
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QBoardStaffEntity STAFF = QBoardStaffEntity.boardStaffEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QBoardStaffUsageEntity USAGE = QBoardStaffUsageEntity.boardStaffUsageEntity;
    private static final QBoardDefaultsEntity DEFAULTS = QBoardDefaultsEntity.boardDefaultsEntity;
    private static final QRoleEntity ROLE = QRoleEntity.roleEntity;
    private static final QRolePermissionEntity ROLE_PERMISSION = QRolePermissionEntity.rolePermissionEntity;

    /** 하루 한도의 마지막 기본값(게시판 값 · board_defaults 가 없을 때) — 글 · 댓글 · 제재 */
    private static final int DEFAULT_POSTS = 10;
    private static final int DEFAULT_COMMENTS = 20;
    private static final int DEFAULT_SANCTIONS = 5;

    /**
     * 처리 기록 한 줄.
     *
     * @param kind              POST · COMMENT
     * @param action            HIDE · DELETE
     * @param targetNickname    대상 회원의 지금 닉네임(비회원 · 지워진 회원 null — 그때는 {@code targetAuthorName} 스냅숏)
     * @param restoredByKind    MANAGER · OPERATOR(되돌리지 않았으면 null)
     * @param restoredByName    되돌린 매니저 닉네임 · 운영자 이름
     * @param actorRole         MANAGER · STAFF · OPERATOR(운영자 회원 계정 — {@code actorId} = 그 회원, {@code actorOperatorId} = 운영자)
     * @param actorOperatorId   운영자 회원 계정의 처리면 그 운영자(아니면 null)
     * @param actorOperatorName 그 운영자의 지금 이름(아니면 null)
     */
    public record ActionRow(long id, long boardId, String boardSlug, String boardName, String boardNameEn, Long actorId, String actorNickname, String actorRole,
                            String kind, String action, Long targetUserId, String targetNickname, long postId, Long commentId, String reasonCode, String targetTitle,
                            String targetExcerpt, String targetAuthorName, Instant createdAt, Instant restoredAt, String restoredByKind, Long restoredById,
                            String restoredByName, String restoreReason, TargetState post, TargetState comment, Long actorOperatorId, String actorOperatorName) {

        /** 운영자 회원 계정이 한 처리인가 — 매니저는 되돌리지 못한다(operator-member-account-contract 정책 7) */
        public boolean byOperator() {
            return ROLE_OPERATOR.equals(actorRole);
        }

        /** 화면 종류 — POST_HIDE · POST_DELETE · COMMENT_DELETE */
        public String apiKind() {
            return kind + "_" + action;
        }

        public boolean restored() {
            return restoredAt != null;
        }

        /** 대상 작성자 이름 — 회원의 지금 닉네임, 없으면 처리 때 스냅숏 */
        public String authorName() {
            return targetNickname != null ? targetNickname : targetAuthorName;
        }

        /** 대상(댓글 삭제면 댓글, 글 처리면 글)의 지금 상태 — PUBLIC · HIDDEN · DELETED */
        public String targetState(Instant now) {
            return (KIND_COMMENT.equals(kind) ? comment : post).state(now);
        }
    }

    /** 대상 글·댓글의 지금 상태 칸 — 행이 없으면 {@code exists} 거짓(지운 것으로 본다) */
    public record TargetState(boolean exists, String status, Instant deletedAt, Instant deleteScheduledAt, Instant hiddenAt, Instant hiddenUntil, Instant purgedAt) {

        public String state(Instant now) {
            if (!exists || deletedAt != null || purgedAt != null || (status != null && !PostEntity.PUBLISHED.equals(status))
                    || (deleteScheduledAt != null && !deleteScheduledAt.isAfter(now))) {
                return "DELETED";
            }
            return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now)) ? "HIDDEN" : "PUBLIC";
        }
    }

    /**
     * 그 게시판에서 이 회원의 자리 · 계정 상태 · 역할 권한.
     *
     * @param role        MANAGER · STAFF — 그 게시판 매니저·스텝이 아니면 null
     * @param accountOk   계정 정상(ACTIVE · 지워지지 않음) · 이메일 인증
     * @param roleSeeded  역할 표에 그 역할이 있다 — 없으면(관리자 서버가 내장 역할을 시드하기 전) 권한을 제한하지 않는다({@code MemberRoleQueries} 와 같다)
     * @param permissions 그 역할의 회원 권한 코드
     */
    public record StaffAccess(String role, boolean accountOk, boolean roleSeeded, Set<String> permissions) {

        public boolean manager() {
            return "MANAGER".equals(role);
        }

        /** 자리 + 계정 정상 + 그 권한(매니저 전용 권한은 매니저만) */
        public boolean can(MemberPermission permission) {
            if (role == null || !accountOk || (permission.managerOnly() && !manager())) {
                return false;
            }
            return !roleSeeded || permissions.contains(permission.code());
        }
    }

    /** 그 게시판의 하루 한도(게시판 값 ?? 기본값) — 글(숨기기+삭제) · 댓글 삭제 · 제재 */
    public record Limits(int posts, int comments, int sanctions) {
    }

    /** 그 날 쓴 수 — 제재 · 글 · 댓글 */
    public record Usage(int sanctions, int posts, int comments) {
    }

    /** 처리 기록 거르기 — {@code actorId} 가 있으면 그 처리자 것만(스텝), {@code kind} ALL · POST · COMMENT */
    public record PageFilter(long boardId, Long actorId, String kind) {
    }

    private final BoardStaffActionRepository actions;
    private final EntityManager entityManager;

    public BoardStaffActionStore(JPAQueryFactory query, BoardStaffActionRepository actions, EntityManager entityManager) {
        super(query);
        this.actions = actions;
        this.entityManager = entityManager;
    }

    /** 제재 줄을 뺀 처리 기록(부분 색인 {@code ix_board_staff_actions_*_content} 와 같은 조건) */
    public static BooleanExpression content(QBoardStaffActionEntity a) {
        return a.kind.ne(BoardStaffActionEntity.KIND_SANCTION);
    }

    // ── 판정 ──────────────────────────────────────────────────────────────

    /** 그 게시판에서 이 회원의 자리 · 계정 · 역할 권한. 회원이 없으면 자리 없음 */
    public StaffAccess access(long boardId, long userId) {
        Tuple t = query.select(STAFF.role, U.status, U.deletedAt, U.emailVerifiedAt).from(U).leftJoin(STAFF).on(STAFF.userId.eq(U.id), STAFF.boardId.eq(boardId))
                .where(U.id.eq(userId)).fetchOne();
        if (t == null) {
            return new StaffAccess(null, false, false, Set.of());
        }
        String role = t.get(STAFF.role);
        boolean accountOk = t.get(U.status) == UserStatus.ACTIVE && t.get(U.deletedAt) == null && t.get(U.emailVerifiedAt) != null;
        String roleId = "MANAGER".equals(role) ? MemberRoleIds.BOARD_MANAGER : "STAFF".equals(role) ? MemberRoleIds.BOARD_STAFF : null;
        if (roleId == null) {
            return new StaffAccess(role, accountOk, false, Set.of());
        }
        boolean seeded = query.selectOne().from(ROLE).where(ROLE.roleId.eq(roleId)).fetchFirst() != null;
        Set<String> codes = Set.copyOf(query.select(ROLE_PERMISSION.permissionCode).from(ROLE_PERMISSION).where(ROLE_PERMISSION.roleId.eq(roleId)).fetch());
        return new StaffAccess(role, accountOk, seeded, codes);
    }

    /** 그 게시판의 매니저·스텝인가(대상 보호) */
    public boolean staffOf(long boardId, long userId) {
        return query.selectOne().from(STAFF).where(STAFF.boardId.eq(boardId), STAFF.userId.eq(userId)).fetchFirst() != null;
    }

    /** 그 게시판의 매니저·스텝 회원 ID 전부 */
    public Set<Long> staffIds(long boardId) {
        return Set.copyOf(query.select(STAFF.userId).from(STAFF).where(STAFF.boardId.eq(boardId)).fetch());
    }

    /**
     * 처리 전 판정용 — 글(임시저장 제외)의 게시판. 없으면 빈 값. 엔티티를 미리 읽지 않는 까닭: 같은 영속성 문맥에서 먼저 읽은 글은 뒤의 잠금 조회가 새 값으로 바꾸지 않는다
     * (잠근 뒤의 상태 판정이 낡은 값을 보지 않게 — 글 엔티티는 잠금 조회로만 처음 읽는다). 그래서 칸 하나만 프로젝션으로 읽는다.
     */
    public Optional<Long> publishedPostBoard(long postId) {
        return Optional.ofNullable(query.select(POST.boardId).from(POST).where(POST.id.eq(postId), POST.status.eq(PostEntity.PUBLISHED)).fetchFirst());
    }

    /** 그 글의 댓글인가(파티션 키 post_id) */
    public boolean commentExists(long postId, long commentId) {
        return query.selectOne().from(COMMENT).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId)).fetchFirst() != null;
    }

    /** 회원 닉네임(스냅숏) — 없으면 null */
    public String nickname(long userId) {
        return query.select(PROFILE.nickname).from(PROFILE).where(PROFILE.userId.eq(userId)).fetchFirst();
    }

    // ── 하루 한도 ─────────────────────────────────────────────────────────

    /** 게시판 값 ?? 기본값(board_defaults, 가장 앞 줄) ?? 고정 기본값 — 칸마다 */
    public Limits limits(long boardId) {
        Tuple own = query.select(B.staffPostDailyLimit, B.staffCommentDailyLimit, B.staffSanctionDailyLimit).from(B).where(B.id.eq(boardId)).fetchOne();
        Tuple defaults = query.select(DEFAULTS.staffPostDailyLimit, DEFAULTS.staffCommentDailyLimit, DEFAULTS.staffSanctionDailyLimit).from(DEFAULTS)
                .orderBy(DEFAULTS.id.asc()).fetchFirst();
        return new Limits(
                pick(own == null ? null : own.get(B.staffPostDailyLimit), defaults == null ? null : defaults.get(DEFAULTS.staffPostDailyLimit), DEFAULT_POSTS),
                pick(own == null ? null : own.get(B.staffCommentDailyLimit), defaults == null ? null : defaults.get(DEFAULTS.staffCommentDailyLimit), DEFAULT_COMMENTS),
                pick(own == null ? null : own.get(B.staffSanctionDailyLimit), defaults == null ? null : defaults.get(DEFAULTS.staffSanctionDailyLimit),
                        DEFAULT_SANCTIONS));
    }

    private static int pick(Integer own, Integer defaults, int fallback) {
        return own != null ? own : defaults != null ? defaults : fallback;
    }

    public Usage usage(long boardId, long userId, LocalDate date) {
        Tuple t = query.select(USAGE.sanctions, USAGE.posts, USAGE.comments).from(USAGE)
                .where(USAGE.boardId.eq(boardId), USAGE.userId.eq(userId), USAGE.usageDate.eq(date)).fetchOne();
        return t == null ? new Usage(0, 0, 0) : new Usage(t.get(USAGE.sanctions), t.get(USAGE.posts), t.get(USAGE.comments));
    }

    /**
     * 한도 안에서 하나 쓴다(원자적 증가 — 서버 여러 대가 동시에 불러도 한도를 넘지 않는다). 쓴 뒤의 사용량, 한도를 다 썼으면 빈 값(행을 바꾸지 않는다). 트랜잭션이 롤백되면 차감도 돌아간다.
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT … DO UPDATE … WHERE 칸 < 한도 RETURNING} — 없으면 만들기·한도 안 증가·결과를 한 문장에서 한다.
     *
     * @param kind {@link #KIND_POST}(칸 posts) · {@link #KIND_COMMENT}(칸 comments)
     */
    public Optional<Integer> consume(String kind, long boardId, long userId, LocalDate date, int limit, Instant now) {
        String column = KIND_POST.equals(kind) ? "posts" : "comments"; // 고정 글자 둘 중 하나
        return firstLong(entityManager.createNativeQuery("INSERT INTO board_staff_usage (board_id, user_id, usage_date, " + column + ", updated_at) VALUES (?1, ?2, ?3, 1, ?4) "
                        + "ON CONFLICT (board_id, user_id, usage_date) DO UPDATE SET " + column + " = board_staff_usage." + column + " + 1, updated_at = EXCLUDED.updated_at "
                        + "WHERE board_staff_usage." + column + " < ?5 RETURNING " + column)
                .setParameter(1, boardId).setParameter(2, userId).setParameter(3, date).setParameter(4, ts(now)).setParameter(5, limit).getResultList())
                .map(Long::intValue);
    }

    // ── 처리 기록 쓰기 ─────────────────────────────────────────────────────

    /**
     * 글·댓글 처리 한 줄 — 스냅숏(제목 · 댓글 앞 40자 · 작성자 이름)과 함께. 그 자리에서 내보낸다.
     *
     * @param actorOperatorId 운영자 회원 계정의 처리면 그 운영자({@code actorRole} OPERATOR), 아니면 null
     */
    public void insert(long id, long boardId, long actorId, String actorRole, Long actorOperatorId, String kind, String action, Long targetUserId, long postId,
                       Long commentId, String reasonCode, String title, String excerpt, String authorName, Instant now) {
        actions.persist(BoardStaffActionEntity.content(id, boardId, actorId, actorRole, actorOperatorId, kind, action, targetUserId, postId, commentId, reasonCode, title,
                excerpt, authorName, now));
        actions.flush();
    }

    /** 이 처리를 되돌림으로(조건부 — 이미 되돌렸으면 바꾸지 않는다). 바꿨으면 true */
    public boolean markRestored(long id, String byKind, long byId, String reason, Instant now) {
        return query.update(ACTION).set(ACTION.restoredAt, now).set(ACTION.restoredByKind, byKind).set(ACTION.restoredById, byId).set(ACTION.restoreReason, reason)
                .where(ACTION.id.eq(id), content(ACTION), ACTION.restoredAt.isNull()).execute() == 1;
    }

    /**
     * 그 글의 되돌리지 않은 글 처리({@code action} HIDE · DELETE)를 되돌림으로 채운다 — 운영자 게시글 관리·신고 처리로 풀었을 때(정책 7). 부분 색인 {@code ix_board_staff_actions_open_post}.
     *
     * @return 채운 줄 수
     */
    public int restoreOpenForPost(long postId, String action, String byKind, long byId, String reason, Instant now) {
        return (int) query.update(ACTION).set(ACTION.restoredAt, now).set(ACTION.restoredByKind, byKind).set(ACTION.restoredById, byId).set(ACTION.restoreReason, reason)
                .where(ACTION.postId.eq(postId), ACTION.action.eq(action), ACTION.restoredAt.isNull(), ACTION.kind.eq(KIND_POST)).execute();
    }

    // ── 처리 기록 읽기 ─────────────────────────────────────────────────────

    /**
     * 처리 기록 한 줄 질의 — 게시판 · 처리자 닉네임 · 대상 작성자 지금 닉네임 · 되돌린 매니저 닉네임/운영자 이름 · 대상 글·댓글의 지금 상태 칸을 함께.
     * 글은 id 범위 파티션, 댓글은 post_id 해시 파티션이라 키로만 잇는다. 거르기·차례는 부르는 쪽이 붙인다.
     */
    public JPAQuery<Tuple> actionRows() {
        return query.select(ACTION.id, ACTION.boardId, B.slug, B.name, B.nameEn, ACTION.actorId, ACTOR.nickname, ACTION.actorRole, ACTION.kind, ACTION.action,
                        ACTION.targetUserId, TARGET.nickname, ACTION.postId, ACTION.commentId, ACTION.reasonCode, ACTION.targetTitle, ACTION.targetExcerpt,
                        ACTION.targetAuthorName, ACTION.createdAt, ACTION.restoredAt, ACTION.restoredByKind, ACTION.restoredById, RESTORER.nickname,
                        RESTORER_OPERATOR.operatorName, ACTION.restoreReason,
                        POST.id, POST.status, POST.deletedAt, POST.deleteScheduledAt, POST.hiddenAt, POST.hiddenUntil, POST.purgedAt,
                        COMMENT.id, COMMENT.deletedAt, COMMENT.hiddenAt, COMMENT.hiddenUntil, COMMENT.purgedAt, ACTION.actorOperatorId, ACTOR_OPERATOR.operatorName)
                .from(ACTION).join(B).on(B.id.eq(ACTION.boardId)).leftJoin(ACTOR).on(ACTOR.userId.eq(ACTION.actorId))
                .leftJoin(ACTOR_OPERATOR).on(ACTOR_OPERATOR.id.eq(ACTION.actorOperatorId))
                .leftJoin(TARGET).on(TARGET.userId.eq(ACTION.targetUserId))
                .leftJoin(RESTORER).on(ACTION.restoredByKind.eq(BY_MANAGER), RESTORER.userId.eq(ACTION.restoredById))
                .leftJoin(RESTORER_OPERATOR).on(ACTION.restoredByKind.eq(BY_OPERATOR), RESTORER_OPERATOR.id.eq(ACTION.restoredById))
                .leftJoin(POST).on(POST.id.eq(ACTION.postId))
                .leftJoin(COMMENT).on(ACTION.commentId.isNotNull(), COMMENT.postId.eq(ACTION.postId), COMMENT.id.eq(ACTION.commentId));
    }

    public static ActionRow row(Tuple t) {
        String restoredByKind = t.get(ACTION.restoredByKind);
        String restoredByName = BY_MANAGER.equals(restoredByKind) ? t.get(RESTORER.nickname)
                : BY_OPERATOR.equals(restoredByKind) ? t.get(RESTORER_OPERATOR.operatorName) : null;
        TargetState post = new TargetState(t.get(POST.id) != null, t.get(POST.status), t.get(POST.deletedAt), t.get(POST.deleteScheduledAt), t.get(POST.hiddenAt),
                t.get(POST.hiddenUntil), t.get(POST.purgedAt));
        TargetState comment = new TargetState(t.get(COMMENT.id) != null, PostEntity.PUBLISHED, t.get(COMMENT.deletedAt), null, t.get(COMMENT.hiddenAt),
                t.get(COMMENT.hiddenUntil), t.get(COMMENT.purgedAt));
        return new ActionRow(t.get(ACTION.id), t.get(ACTION.boardId), t.get(B.slug), t.get(B.name), t.get(B.nameEn), t.get(ACTION.actorId), t.get(ACTOR.nickname),
                t.get(ACTION.actorRole), t.get(ACTION.kind), t.get(ACTION.action), t.get(ACTION.targetUserId), t.get(TARGET.nickname), t.get(ACTION.postId),
                t.get(ACTION.commentId), t.get(ACTION.reasonCode), t.get(ACTION.targetTitle), t.get(ACTION.targetExcerpt), t.get(ACTION.targetAuthorName),
                t.get(ACTION.createdAt), t.get(ACTION.restoredAt), restoredByKind, t.get(ACTION.restoredById), restoredByName, t.get(ACTION.restoreReason), post,
                comment, t.get(ACTION.actorOperatorId), t.get(ACTOR_OPERATOR.operatorName));
    }

    /** 처리 기록 하나(제재 줄은 없다고 본다) */
    public Optional<ActionRow> find(long id) {
        return Optional.ofNullable(actionRows().where(ACTION.id.eq(id), content(ACTION)).fetchOne()).map(BoardStaffActionStore::row);
    }

    public long total(PageFilter filter) {
        return zero(query.select(ACTION.count()).from(ACTION).where(where(filter)).fetchOne());
    }

    /** 최신 것이 위 — 색인 {@code ix_board_staff_actions_board_content}(매니저) · {@code ix_board_staff_actions_actor_content}(스텝) */
    public List<ActionRow> page(PageFilter filter, int offset, int size) {
        return actionRows().where(where(filter)).orderBy(ACTION.createdAt.desc(), ACTION.id.desc()).offset(offset).limit(size).fetch().stream()
                .map(BoardStaffActionStore::row).toList();
    }

    private static BooleanExpression where(PageFilter f) {
        BooleanExpression where = ACTION.boardId.eq(f.boardId()).and(content(ACTION));
        if (f.actorId() != null) {
            where = where.and(ACTION.actorId.eq(f.actorId()));
        }
        if (KIND_POST.equals(f.kind()) || KIND_COMMENT.equals(f.kind())) {
            where = where.and(ACTION.kind.eq(f.kind()));
        }
        return where;
    }
}
