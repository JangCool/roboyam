package com.community.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.board.dto.BoardWriteRestriction;
import com.community.permission.entity.MemberPermission;
import com.community.role.entity.MemberRoleIds;
import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.entity.SanctionStatus;
import com.community.sanction.repository.master.BoardSanctionRepository;
import com.community.sanction.repository.master.BoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.BoardSanctionStore.ReviewRow;
import com.community.sanction.repository.master.BoardSanctionStore.StaffGate;
import com.community.sanction.repository.master.BoardStaffActionRepository;
import com.community.sanction.repository.master.BoardStaffActionStore;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import com.community.sanction.repository.master.BoardStaffActionStore.StaffAccess;
import com.community.sanction.repository.master.MemberSanctionRepository;
import com.community.sanction.repository.master.MemberSanctionStore;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import com.community.zconfiguration.time.ServiceTime;
import com.community.testsupport.BoardFixture;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MemberFixture;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 제재·게시판 제재·스텝 처리 저장소가 운영과 같은 JPA·QueryDSL 로 돈다 — 조건부 상태 전이(한 번만 이긴다), DB 번호 읽어 오기, 남긴 native 문장(ON CONFLICT · 윈도 함수 ·
 * DELETE RETURNING), 쓰기 잠금(프로젝션 + {@code PESSIMISTIC_WRITE} 가 실제 행 잠금인지), 그리고 JDBC 시절 SQL 과 같은 결과인지(같은 자료에 예전 문장을 돌려 견준다). 실제 PostgreSQL.
 * 시험 DB 를 다른 시험과 같이 쓰므로 이 시험이 만든 회원·게시판만 본다.
 */
class SanctionStoresJpaTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(931);

    private static JdbcTemplate jdbc;
    private static DataSource dataSource;
    private static TransactionTemplate tx;
    private static MemberFixture members;
    private static BoardFixture boards;
    private static MemberSanctionStore sanctions;
    private static BoardSanctionStore boardSanctions;
    private static BoardStaffActionStore staffActions;

    @BeforeAll
    static void setUp() {
        ApplicationContext context = JpaTestContext.context();
        dataSource = JpaTestContext.dataSource();
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(JpaTestContext.transactions());
        members = new MemberFixture(context);
        boards = new BoardFixture(context);
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(JpaMasterConfig.ENTITY_MANAGER, EntityManagerFactory.class));
        sanctions = new MemberSanctionStore(JpaTestContext.query(), context.getBean(MemberSanctionRepository.class), em);
        staffActions = new BoardStaffActionStore(JpaTestContext.query(), context.getBean(BoardStaffActionRepository.class), em);
        boardSanctions = new BoardSanctionStore(JpaTestContext.query(), sanctions, context.getBean(BoardSanctionRepository.class),
                context.getBean(BoardStaffActionRepository.class), em);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    private static long member(boolean verified) {
        long id = IDS.nextId();
        members.user(id, "sanction-" + id + "@test.local", verified);
        members.profile(id, "제" + Long.toString(id, 36).substring(4));
        return id;
    }

    private static long operator() {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "op-" + id + "@test.local", "운영" + Long.toString(id, 36));
        return id;
    }

    private static long board() {
        long id = IDS.nextId();
        boards.board(id, "sb" + Long.toString(id, 36), "제재판 " + id, 900);
        return id;
    }

    private static String userStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
    }

    /** 다른 연결에서 그 행을 기다리지 않고 키 나눔 잠금(FK 확인과 같은 세기) 해 본다 — 전환 전 세기 {@code FOR UPDATE} 로 잠겨 있으면 55P03 */
    private static void assertRowLocked(String table, long id) {
        try (Connection other = dataSource.getConnection(); Statement st = other.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("SELECT 1 FROM " + table + " WHERE id = " + id + " FOR KEY SHARE NOWAIT"))
                    .isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("55P03"));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── 사이트 제재 ─────────────────────────────────────────────────────

    @Test
    void 사이트_제재_넣기는_DB_번호를_돌려주고_해제는_한_번만_이기며_정지가_없으면_회원을_되돌린다() {
        long user = member(true);
        long op = operator();
        long board = board();
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'STAFF')", board, user);
        Instant now = now();
        long warningId = IDS.nextId();
        long suspendId = IDS.nextId();

        inTx(() -> {
            assertThat(sanctions.lockMember(user)).hasValueSatisfying(m -> {
                assertThat(m.gone()).isFalse();
                assertThat(m.status()).isEqualTo("ACTIVE");
                assertThat(m.nickname()).isNotNull();
            });
            assertRowLocked("users", user);
            long no = sanctions.insert(warningId, user, SanctionKind.WARNING, 1, false, "경고 사유", now, null, op, null, now);
            assertThat(no).isPositive().isEqualTo(jdbc.queryForObject("SELECT sanction_no FROM member_sanctions WHERE id = ?", Long.class, warningId));
            sanctions.insert(suspendId, user, SanctionKind.SUSPEND_3, null, false, "정지 사유", now, now.plus(Duration.ofDays(3)), op, null, now);
            sanctions.restrictMember(user, "SUSPENDED", now);
            assertThat(sanctions.endBoardStaff(user)).containsExactly(new MemberSanctionStore.StaffRow(board, "STAFF"));
            return null;
        });
        assertThat(userStatus(user)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_staff WHERE user_id = ?", Integer.class, user)).isZero();

        SanctionRow warning = sanctions.find(warningId).orElseThrow();
        assertThat(warning.kind()).isEqualTo(SanctionKind.WARNING);
        assertThat(warning.warningNo()).isEqualTo(1);
        assertThat(warning.issuedByName()).startsWith("운영");
        assertThat(warning.userStatus()).isEqualTo("SUSPENDED");
        assertThat(warning.appealId()).isNull();
        assertThat(sanctions.userOf(suspendId)).contains(user);
        assertThat(sanctions.allOfUser(user)).extracting(SanctionRow::id).containsExactlyInAnyOrder(warningId, suspendId);
        assertThat(sanctions.ofUser(user, 1)).hasSize(1);
        assertThat(sanctions.pendingWarnings(user)).isEqualTo(1);
        assertThat(sanctions.runningUntil(user, now)).contains(now.plus(Duration.ofDays(3)));

        assertThat(inTx(() -> sanctions.acknowledge(warningId, user + 1, now))).isFalse();
        assertThat(inTx(() -> sanctions.acknowledge(warningId, user, now))).isTrue();
        assertThat(sanctions.pendingWarnings(user)).isZero();

        assertThat(inTx(() -> sanctions.lift(warningId, op, "경고는 못 푼다", now))).isFalse();
        assertThat(inTx(() -> sanctions.lift(suspendId, op, "해제", now))).isTrue();
        assertThat(inTx(() -> sanctions.lift(suspendId, op, "또 해제", now))).isFalse();
        SanctionRow lifted = sanctions.find(suspendId).orElseThrow();
        assertThat(lifted.status()).isEqualTo(SanctionStatus.LIFTED);
        assertThat(lifted.liftedByName()).startsWith("운영");
        assertThat(sanctions.needsSettle(user, now)).isTrue(); // 막힌 상태인데 진행 중 정지가 없다
        assertThat(inTx(() -> sanctions.releaseMember(user, now))).isTrue();
        assertThat(userStatus(user)).isEqualTo("ACTIVE");
        assertThat(sanctions.needsSettle(user, now)).isFalse();
        assertThat(inTx(() -> sanctions.releaseMember(user, now))).isFalse();
    }

    @Test
    void 끝이_지난_정지는_끝_시각으로_끝내고_영구_정지는_진행_중_정지를_대체한다() {
        long user = member(false);
        long op = operator();
        Instant now = now();
        long dueId = IDS.nextId();
        inTx(() -> {
            sanctions.insert(dueId, user, SanctionKind.SUSPEND_3, null, false, "지난 정지", now.minus(Duration.ofDays(4)), now.minus(Duration.ofDays(1)), op, null,
                    now.minus(Duration.ofDays(4)));
            sanctions.restrictMember(user, "SUSPENDED", now);
            return null;
        });
        assertThat(sanctions.needsSettle(user, now)).isTrue();
        assertThat(inTx(() -> sanctions.endDue(user, now))).isEqualTo(1);
        SanctionRow ended = sanctions.find(dueId).orElseThrow();
        assertThat(ended.status()).isEqualTo(SanctionStatus.ENDED);
        assertThat(ended.endedAt()).isEqualTo(now.minus(Duration.ofDays(1)));
        assertThat(inTx(() -> sanctions.releaseMember(user, now))).isTrue();

        long runningId = IDS.nextId();
        inTx(() -> sanctions.insert(runningId, user, SanctionKind.SUSPEND_7, null, false, "정지", now, now.plus(Duration.ofDays(7)), op, null, now));
        assertThat(inTx(() -> sanctions.endRunning(user, now))).contains(runningId);
        assertThat(sanctions.find(runningId).orElseThrow().endedAt()).isEqualTo(now);
        assertThat(inTx(() -> sanctions.endRunning(user, now))).isEmpty();
    }

    @Test
    void 경고를_무효로_하면_남은_경고_번호를_받은_차례로_다시_매긴다() {
        long user = member(false);
        long op = operator();
        Instant base = now().minus(Duration.ofHours(3));
        long first = IDS.nextId();
        long second = IDS.nextId();
        long third = IDS.nextId();
        inTx(() -> {
            sanctions.insert(first, user, SanctionKind.WARNING, 1, false, "1", base, null, op, null, base);
            sanctions.insert(second, user, SanctionKind.WARNING, 2, false, "2", base.plusSeconds(60), null, op, null, base.plusSeconds(60));
            sanctions.insert(third, user, SanctionKind.WARNING, 2, false, "3", base.plusSeconds(120), null, op, null, base.plusSeconds(120));
            return null;
        });
        Instant now = now();
        assertThat(inTx(() -> {
            boolean voided = sanctions.voidSanction(first, now);
            sanctions.renumberWarnings(user, now);
            return voided;
        })).isTrue();
        assertThat(inTx(() -> sanctions.voidSanction(first, now))).isFalse();
        assertThat(sanctions.find(first).orElseThrow().warningNo()).isNull();
        assertThat(sanctions.find(second).orElseThrow().warningNo()).isEqualTo(1);
        assertThat(sanctions.find(third).orElseThrow().warningNo()).isEqualTo(2);
    }

    @Test
    void 이의제기_접수는_한_번만_받고_잠근_뒤_답은_대기일_때만_한_번() {
        long user = member(false);
        long op = operator();
        Instant now = now();
        long sanctionId = IDS.nextId();
        inTx(() -> sanctions.insert(sanctionId, user, SanctionKind.WARNING, 1, false, "경고", now, null, op, null, now));
        long appealId = IDS.nextId();
        Optional<Long> no = inTx(() -> sanctions.insertAppeal(appealId, sanctionId, user, "이의제기 사유 열 글자 이상", now));
        assertThat(no).hasValueSatisfying(n -> assertThat(n).isPositive());
        assertThat(inTx(() -> sanctions.insertAppeal(IDS.nextId(), sanctionId, user, "두 번째 이의제기 사유입니다", now))).isEmpty();

        AppealRow appeal = sanctions.findAppeal(appealId).orElseThrow();
        assertThat(appeal.no()).isEqualTo(no.get());
        assertThat(appeal.status()).isEqualTo(AppealStatus.PENDING);
        assertThat(appeal.board()).isFalse();
        assertThat(sanctions.appealOf(sanctionId)).map(AppealRow::id).contains(appealId);
        assertThat(sanctions.appealsOfUser(user)).extracting(AppealRow::id).containsExactly(appealId);
        assertThat(sanctions.find(sanctionId).orElseThrow().appealStatus()).isEqualTo(AppealStatus.PENDING);

        inTx(() -> {
            assertThat(sanctions.lockAppeal(appealId)).hasValueSatisfying(a -> assertThat(a.answeredByName()).isNull());
            assertRowLocked("sanction_appeals", appealId);
            assertThat(sanctions.answerAppeal(appealId, AppealStatus.REJECTED, "기각", op, now)).isTrue();
            assertThat(sanctions.answerAppeal(appealId, AppealStatus.ACCEPTED, "인용", op, now)).isFalse();
            return null;
        });
        AppealRow answered = sanctions.findAppeal(appealId).orElseThrow();
        assertThat(answered.status()).isEqualTo(AppealStatus.REJECTED);
        assertThat(answered.answeredByName()).startsWith("운영");
    }

    // ── 게시판 제재 ─────────────────────────────────────────────────────

    /** JDBC 시절 판정 한 문장(역할 표가 없으면 제한하지 않는다) */
    private static StaffGate oldGate(long boardId, long userId) {
        String roleOf = "CASE s.role WHEN 'MANAGER' THEN '" + MemberRoleIds.BOARD_MANAGER + "' ELSE '" + MemberRoleIds.BOARD_STAFF + "' END";
        String has = "(EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = " + roleOf + " AND rp.permission_code = ?) "
                + "OR NOT EXISTS (SELECT 1 FROM roles r WHERE r.role_id = " + roleOf + "))";
        List<StaffGate> rows = jdbc.query("SELECT s.role, (u.status = 'ACTIVE' AND u.deleted_at IS NULL AND u.email_verified_at IS NOT NULL), "
                        + "(s.role IS NOT NULL AND " + has + "), (s.role = 'MANAGER' AND " + has + ") "
                        + "FROM users u LEFT JOIN board_staff s ON s.user_id = u.id AND s.board_id = ? WHERE u.id = ?",
                (rs, i) -> new StaffGate(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)),
                MemberPermission.BOARD_SANCTION_ISSUE.code(), MemberPermission.BOARD_STAFF_ACTION_RESTORE.code(), boardId, userId);
        return rows.isEmpty() ? new StaffGate(null, false, false, false) : rows.get(0);
    }

    @Test
    void 매니저_판정과_하루_한도는_예전_문장과_같고_한도_증가는_넘지_않는다() {
        long board = board();
        long manager = member(true);
        long staff = member(false);
        long outsider = member(true);
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER'), (?, ?, 'STAFF')", board, manager, board, staff);

        for (long user : new long[]{manager, staff, outsider, IDS.nextId()}) {
            assertThat(boardSanctions.gate(board, user)).isEqualTo(oldGate(board, user));
        }
        StaffGate gate = boardSanctions.gate(board, manager);
        assertThat(gate.manager()).isTrue();
        assertThat(gate.accountOk()).isTrue();
        assertThat(boardSanctions.gate(board, staff).accountOk()).isFalse(); // 이메일 미인증

        StaffAccess access = staffActions.access(board, manager);
        assertThat(access.role()).isEqualTo("MANAGER");
        assertThat(access.roleSeeded()).isEqualTo(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM roles WHERE role_id = ?)", Boolean.class,
                MemberRoleIds.BOARD_MANAGER)));
        assertThat(access.permissions()).containsExactlyInAnyOrderElementsOf(jdbc.queryForList("SELECT permission_code FROM role_permissions WHERE role_id = ?",
                String.class, MemberRoleIds.BOARD_MANAGER));
        assertThat(staffActions.access(board, outsider).role()).isNull();
        assertThat(staffActions.access(board, IDS.nextId()).accountOk()).isFalse();

        assertThat(boardSanctions.boardKind(board)).contains("GENERAL");
        assertThat(boardSanctions.staffOf(board, staff)).isTrue();
        assertThat(boardSanctions.staffOf(board, outsider)).isFalse();
        assertThat(staffActions.staffIds(board)).containsExactlyInAnyOrder(manager, staff);
        assertThat(boardSanctions.dailyLimit(board)).isEqualTo(jdbc.queryForObject("SELECT coalesce((SELECT staff_sanction_daily_limit FROM boards WHERE id = ?), "
                + "(SELECT staff_sanction_daily_limit FROM board_defaults ORDER BY id LIMIT 1), 5)", Integer.class, board));
        BoardStaffActionStore.Limits limits = staffActions.limits(board);
        assertThat(limits).isEqualTo(jdbc.queryForObject("SELECT coalesce(b.staff_post_daily_limit, d.staff_post_daily_limit, 10), "
                        + "coalesce(b.staff_comment_daily_limit, d.staff_comment_daily_limit, 20), coalesce(b.staff_sanction_daily_limit, d.staff_sanction_daily_limit, 5) "
                        + "FROM (SELECT 1) one LEFT JOIN boards b ON b.id = ? LEFT JOIN LATERAL (SELECT staff_post_daily_limit, staff_comment_daily_limit, "
                        + "staff_sanction_daily_limit FROM board_defaults ORDER BY id LIMIT 1) d ON true",
                (rs, i) -> new BoardStaffActionStore.Limits(rs.getInt(1), rs.getInt(2), rs.getInt(3)), board));

        Instant now = now();
        LocalDate today = ServiceTime.dateOf(now);
        assertThat(boardSanctions.used(board, manager, today)).isZero();
        assertThat(inTx(() -> boardSanctions.consume(board, manager, today, 2, now))).contains(1);
        assertThat(inTx(() -> boardSanctions.consume(board, manager, today, 2, now))).contains(2);
        assertThat(inTx(() -> boardSanctions.consume(board, manager, today, 2, now))).isEmpty();
        assertThat(boardSanctions.used(board, manager, today)).isEqualTo(2);
        assertThat(inTx(() -> staffActions.consume(BoardStaffActionStore.KIND_POST, board, manager, today, 1, now))).contains(1);
        assertThat(inTx(() -> staffActions.consume(BoardStaffActionStore.KIND_POST, board, manager, today, 1, now))).isEmpty();
        assertThat(inTx(() -> staffActions.consume(BoardStaffActionStore.KIND_COMMENT, board, manager, today, 5, now))).contains(1);
        assertThat(staffActions.usage(board, manager, today)).isEqualTo(new BoardStaffActionStore.Usage(2, 1, 1));
        assertThat(staffActions.usage(board, outsider, today)).isEqualTo(new BoardStaffActionStore.Usage(0, 0, 0));
    }

    @Test
    void 게시판_제재_한_줄과_조건부_해제_무효_확인_내역_거르기() {
        long board = board();
        long manager = member(true);
        long target = member(false);
        long op = operator();
        long postId = IDS.nextId();
        boards.post(postId, board, target, "계기 글", "본문");
        long commentId = IDS.nextId();
        boards.comment(commentId, postId, board, target, "댓글");
        assertThat(boardSanctions.post(postId)).contains(new BoardSanctionStore.PostSource(board, target, "계기 글"));
        assertThat(boardSanctions.comment(postId, commentId)).contains(new BoardSanctionStore.PostSource(board, target, null));
        assertThat(boardSanctions.comment(postId, commentId + 1)).isEmpty();

        Instant now = now();
        long restrictId = IDS.nextId();
        long warningId = IDS.nextId();
        long no = inTx(() -> {
            assertThat(boardSanctions.lockTarget(target)).hasValueSatisfying(t -> assertThat(t.gone()).isFalse());
            assertRowLocked("users", target);
            long n = boardSanctions.insert(restrictId, board, target, BoardSanctionKind.RESTRICT_1, "SPAM", null, manager, "MANAGER", null, postId, commentId, "계기 글",
                    now, now.plus(Duration.ofDays(1)), now);
            boardSanctions.insertAction(IDS.nextId(), board, manager, "MANAGER", null, "ISSUE", target, restrictId, postId, commentId, null, now);
            boardSanctions.insert(warningId, board, target, BoardSanctionKind.WARNING, "ABUSE", "말 조심", manager, "MANAGER", null, null, null, null, now, null,
                    now.plusSeconds(1));
            return n;
        });
        assertThat(no).isEqualTo(jdbc.queryForObject("SELECT sanction_no FROM board_sanctions WHERE id = ?", Long.class, restrictId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_staff_actions WHERE board_sanction_id = ? AND kind = 'SANCTION'", Integer.class, restrictId)).isOne();

        BoardSanctionRow row = boardSanctions.find(restrictId).orElseThrow();
        assertThat(row.boardSlug()).startsWith("sb");
        assertThat(row.issuedByNickname()).isNotNull();
        assertThat(row.sourceTitle()).isEqualTo("계기 글");
        assertThat(row.userStatus()).isEqualTo("ACTIVE");
        assertThat(row.runningAt(now)).isTrue();
        assertThat(boardSanctions.ownerOf(restrictId)).hasValueSatisfying(o -> assertThat(o).containsExactly(target, board));
        assertThat(boardSanctions.byIds(List.of(restrictId, warningId))).containsOnlyKeys(restrictId, warningId);
        assertThat(boardSanctions.ofUser(target, 10)).extracting(BoardSanctionRow::id).containsExactly(warningId, restrictId);
        assertThat(boardSanctions.ofUserBoard(target, board, 1)).extracting(BoardSanctionRow::id).containsExactly(warningId);
        assertThat(boardSanctions.ofUserBoardBetween(target, board, now.minusSeconds(1), now.plusSeconds(5))).extracting(BoardSanctionRow::id)
                .containsExactly(restrictId, warningId);
        assertThat(boardSanctions.counts(target, board)).isEqualTo(new BoardSanctionStore.Counts(1, 1));
        assertThat(boardSanctions.counts(target, board + 1)).isEqualTo(new BoardSanctionStore.Counts(0, 0));
        assertThat(boardSanctions.running(target, board, now)).contains(new BoardWriteRestriction(restrictId, now.plus(Duration.ofDays(1)), "SPAM"));
        assertThat(boardSanctions.runningOfUser(target, now)).containsOnlyKeys(board);
        assertThat(boardSanctions.pendingWarnings(target, board, 5)).extracting(BoardSanctionStore.PendingWarning::id).containsExactly(warningId);

        assertStaffScopes(board, manager, now);

        assertThat(inTx(() -> boardSanctions.acknowledge(warningId, target, now))).isTrue();
        assertThat(boardSanctions.pendingWarnings(target, board, 5)).isEmpty();
        assertThat(inTx(() -> boardSanctions.acknowledge(warningId, manager, now))).isFalse();

        assertThat(inTx(() -> boardSanctions.lift(warningId, manager, null, "경고", now))).isFalse();
        assertThat(inTx(() -> boardSanctions.lift(restrictId, manager, null, "풀어 줌", now))).isTrue();
        assertThat(inTx(() -> boardSanctions.lift(restrictId, null, op, "또", now))).isFalse();
        BoardSanctionRow lifted = boardSanctions.find(restrictId).orElseThrow();
        assertThat(lifted.status()).isEqualTo(SanctionStatus.LIFTED);
        assertThat(lifted.liftedByType()).isEqualTo("MANAGER");
        assertThat(lifted.liftedByUser()).isEqualTo(manager);
        assertThat(lifted.liftedByOperator()).isNull();
        assertThat(lifted.liftedByUserNickname()).isNotNull();

        assertThat(inTx(() -> boardSanctions.voidSanction(warningId, "OPERATOR", op, "무효 사유", now))).isTrue();
        assertThat(inTx(() -> boardSanctions.voidSanction(warningId, "OPERATOR", op, "또", now))).isFalse();
        BoardSanctionRow voided = boardSanctions.find(warningId).orElseThrow();
        assertThat(voided.voidedByName()).startsWith("운영");
        assertThat(boardSanctions.counts(target, board)).isEqualTo(new BoardSanctionStore.Counts(0, 1));
        assertStaffScopes(board, manager, now);

        // 끝이 지난 쓰기 제한
        long dueId = IDS.nextId();
        inTx(() -> boardSanctions.insert(dueId, board, target, BoardSanctionKind.RESTRICT_3, "SPAM", null, manager, "MANAGER", null, null, null, null,
                now.minus(Duration.ofDays(4)), now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(4))));
        assertThat(boardSanctions.running(target, board, now)).isEmpty();
        assertThat(inTx(() -> boardSanctions.endDue(target, board, now))).isEqualTo(1);
        assertThat(boardSanctions.find(dueId).orElseThrow().endedAt()).isEqualTo(now.minus(Duration.ofDays(1)));
        assertStaffScopes(board, manager, now);
    }

    /** 매니저·스텝 내역의 범위 거르기가 예전 문장과 같은 줄을 같은 차례로 준다 */
    private static void assertStaffScopes(long board, long issuer, Instant now) {
        Timestamp at = Timestamp.from(now);
        Map<String, String> scopes = Map.of("ALL", "", "ACTIVE", " AND s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND s.ends_at > ?",
                "ENDED", " AND NOT (s.kind <> 'WARNING' AND s.status = 'ACTIVE' AND s.ends_at > ?) AND NOT (s.kind = 'WARNING' AND s.status = 'ACTIVE')");
        scopes.forEach((scope, where) -> {
            for (Long by : new Long[]{null, issuer}) {
                String sql = "SELECT s.id FROM board_sanctions s WHERE s.board_id = ?" + (by == null ? "" : " AND s.issued_by = " + by) + where
                        + " ORDER BY s.created_at DESC, s.id DESC";
                List<Long> expected = where.isEmpty() ? jdbc.queryForList(sql, Long.class, board) : jdbc.queryForList(sql, Long.class, board, at);
                assertThat(boardSanctions.staffPage(board, by, scope, now, 0, 50)).extracting(BoardSanctionRow::id).as(scope).containsExactlyElementsOf(expected);
                assertThat(boardSanctions.staffTotal(board, by, scope, now)).as(scope).isEqualTo(expected.size());
            }
        });
    }

    @Test
    void 반복_검토는_한_번만_열리고_잠근_뒤_닫으며_무효로_한_제재_목록을_배열로_남긴다() {
        long board = board();
        long otherBoard = board();
        long manager = member(true);
        long target = member(false);
        long op = operator();
        Instant now = now();
        long s1 = IDS.nextId();
        long s2 = IDS.nextId();
        long s3 = IDS.nextId();
        inTx(() -> {
            boardSanctions.insert(s1, board, target, BoardSanctionKind.WARNING, "SPAM", null, manager, "MANAGER", null, null, null, null, now, null, now);
            boardSanctions.insert(s2, board, target, BoardSanctionKind.RESTRICT_1, "SPAM", null, manager, "MANAGER", null, null, null, null, now,
                    now.plus(Duration.ofDays(1)), now.plusSeconds(1));
            boardSanctions.insert(s3, otherBoard, target, BoardSanctionKind.WARNING, "SPAM", null, manager, "MANAGER", null, null, null, null, now, null,
                    now.plusSeconds(2));
            return null;
        });
        assertThat(boardSanctions.countValidAfter(target, board, now.minusSeconds(1))).isEqualTo(2);
        assertThat(boardSanctions.boardsValidAfter(target, now.minusSeconds(1))).isEqualTo(2);
        assertThat(boardSanctions.boardSummaries(target, now.minusSeconds(1), now)).isEqualTo(jdbc.query(
                "SELECT s.board_id, b.slug, b.name, b.name_en, count(*) FILTER (WHERE s.status <> 'VOIDED' AND s.created_at > ?), "
                        + "count(*) FILTER (WHERE s.status <> 'VOIDED'), max(s.ends_at) FILTER (WHERE s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND s.ends_at > ?) "
                        + "FROM board_sanctions s JOIN boards b ON b.id = s.board_id WHERE s.user_id = ? GROUP BY s.board_id, b.slug, b.name, b.name_en ORDER BY b.name, s.board_id",
                (rs, i) -> new BoardSanctionStore.BoardSummary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()), Timestamp.from(now.minusSeconds(1)), Timestamp.from(now), target));

        assertThat(boardSanctions.hasOpenReview(target, board)).isFalse();
        assertThat(boardSanctions.lastClosedAt(target, board)).isEmpty();
        long reviewId = IDS.nextId();
        Optional<Long> reviewNo = inTx(() -> boardSanctions.openReview(reviewId, board, target, 2, null, now));
        assertThat(reviewNo).isPresent();
        assertThat(inTx(() -> boardSanctions.openReview(IDS.nextId(), board, target, 3, null, now))).isEmpty();
        assertThat(boardSanctions.hasOpenReview(target, board)).isTrue();
        assertThat(boardSanctions.openReviewsOf(target)).isEqualTo(1);
        ReviewRow open = boardSanctions.findReview(reviewId).orElseThrow();
        assertThat(open.no()).isEqualTo(reviewNo.get());
        assertThat(open.open()).isTrue();
        assertThat(open.voidedSanctionIds()).isEmpty();
        assertThat(open.openedCount()).isEqualTo(2);

        long siteSanction = IDS.nextId();
        inTx(() -> sanctions.insert(siteSanction, target, SanctionKind.WARNING, 1, false, "반복", now, null, op, null, now));
        assertThat(inTx(() -> {
            assertThat(boardSanctions.lockReview(reviewId)).hasValueSatisfying(l -> assertThat(l).containsExactly(target, board, 1));
            assertRowLocked("board_sanction_reviews", reviewId);
            return boardSanctions.closeReview(reviewId, "SITE_SANCTIONED", "메모", siteSanction, List.of(s1, s2), 2, 2, op, now);
        })).isTrue();
        assertThat(inTx(() -> boardSanctions.closeReview(reviewId, "KEPT", null, null, List.of(), 0, 0, op, now))).isFalse();
        ReviewRow closed = boardSanctions.findReview(reviewId).orElseThrow();
        assertThat(closed.open()).isFalse();
        assertThat(closed.voidedSanctionIds()).containsExactly(s1, s2);
        assertThat(closed.siteSanctionKind()).isEqualTo("WARNING");
        assertThat(closed.siteSanctionNo()).isPositive();
        assertThat(closed.closedByName()).startsWith("운영");
        assertThat(inTx(() -> boardSanctions.lockReview(reviewId))).hasValueSatisfying(l -> assertThat(l[2]).isZero());
        assertThat(boardSanctions.lastClosedAt(target, board)).contains(now);
        assertThat(boardSanctions.hasOpenReview(target, board)).isFalse();

        // 게시판 제재 이의제기
        long appealId = IDS.nextId();
        assertThat(inTx(() -> boardSanctions.insertAppeal(appealId, s2, target, "게시판 제재 이의 사유입니다", now))).isPresent();
        assertThat(inTx(() -> boardSanctions.insertAppeal(IDS.nextId(), s2, target, "게시판 제재 이의 사유 둘째", now))).isEmpty();
        assertThat(inTx(() -> {
            Optional<Long> locked = boardSanctions.lockPendingAppeal(s2);
            assertRowLocked("sanction_appeals", appealId);
            return locked;
        })).contains(appealId);
        assertThat(inTx(() -> boardSanctions.lockPendingAppeal(s1))).isEmpty();
        assertThat(boardSanctions.appealsOfUser(target)).containsOnlyKeys(s2);
        assertThat(boardSanctions.find(s2).orElseThrow().appealId()).isEqualTo(appealId);
        assertThat(sanctions.appealsOfUser(target)).isEmpty(); // 사이트 제재 이의제기만
    }

    // ── 스텝 처리 기록 ──────────────────────────────────────────────────

    @Test
    void 스텝_처리_기록은_대상_상태와_함께_읽고_되돌림은_한_번만() {
        long board = board();
        long manager = member(true);
        long author = member(false);
        long op = operator();
        long postId = IDS.nextId();
        boards.post(postId, board, author, "숨길 글", "본문");
        long commentId = IDS.nextId();
        boards.comment(commentId, postId, board, author, "지울 댓글");
        assertThat(staffActions.publishedPostBoard(postId)).contains(board);
        assertThat(staffActions.publishedPostBoard(postId + 1)).isEmpty();
        assertThat(staffActions.commentExists(postId, commentId)).isTrue();
        assertThat(staffActions.nickname(author)).isNotNull();
        assertThat(staffActions.nickname(IDS.nextId())).isNull();

        Instant now = now();
        long hideId = IDS.nextId();
        long deleteId = IDS.nextId();
        long operatorActionId = IDS.nextId();
        inTx(() -> {
            staffActions.insert(hideId, board, manager, "MANAGER", null, BoardStaffActionStore.KIND_POST, BoardStaffActionStore.ACTION_HIDE, author, postId, null, "SPAM",
                    "숨길 글", null, "작성자", now);
            staffActions.insert(deleteId, board, manager, "MANAGER", null, BoardStaffActionStore.KIND_COMMENT, BoardStaffActionStore.ACTION_DELETE, author, postId,
                    commentId, "ABUSE", "숨길 글", "지울 댓글", "작성자", now.plusSeconds(1));
            staffActions.insert(operatorActionId, board, manager, BoardStaffActionStore.ROLE_OPERATOR, op, BoardStaffActionStore.KIND_POST,
                    BoardStaffActionStore.ACTION_DELETE, author, postId, null, "OFF_TOPIC", "숨길 글", null, "작성자", now.plusSeconds(2));
            return null;
        });

        ActionRow hide = staffActions.find(hideId).orElseThrow();
        assertThat(hide.apiKind()).isEqualTo("POST_HIDE");
        assertThat(hide.boardSlug()).startsWith("sb");
        assertThat(hide.actorNickname()).isNotNull();
        assertThat(hide.targetNickname()).isNotNull();
        assertThat(hide.targetState(now)).isEqualTo("PUBLIC");
        assertThat(hide.restoredByName()).isNull();
        assertThat(staffActions.find(deleteId).orElseThrow().targetState(now)).isEqualTo("PUBLIC");
        assertThat(staffActions.find(operatorActionId).orElseThrow().actorOperatorName()).startsWith("운영");

        assertThat(staffActions.page(new BoardStaffActionStore.PageFilter(board, null, "ALL"), 0, 10)).extracting(ActionRow::id)
                .containsExactly(operatorActionId, deleteId, hideId);
        assertThat(staffActions.page(new BoardStaffActionStore.PageFilter(board, null, "COMMENT"), 0, 10)).extracting(ActionRow::id).containsExactly(deleteId);
        assertThat(staffActions.page(new BoardStaffActionStore.PageFilter(board, null, "ALL"), 1, 1)).extracting(ActionRow::id).containsExactly(deleteId);
        assertThat(staffActions.total(new BoardStaffActionStore.PageFilter(board, manager, "POST"))).isEqualTo(2);
        assertThat(staffActions.total(new BoardStaffActionStore.PageFilter(board, author, "ALL"))).isZero();

        assertThat(inTx(() -> staffActions.markRestored(hideId, BoardStaffActionStore.BY_MANAGER, manager, "되돌림", now))).isTrue();
        assertThat(inTx(() -> staffActions.markRestored(hideId, BoardStaffActionStore.BY_MANAGER, manager, "또", now))).isFalse();
        ActionRow restored = staffActions.find(hideId).orElseThrow();
        assertThat(restored.restored()).isTrue();
        assertThat(restored.restoredByName()).isEqualTo(restored.actorNickname()); // 같은 매니저

        assertThat(inTx(() -> staffActions.restoreOpenForPost(postId, BoardStaffActionStore.ACTION_DELETE, BoardStaffActionStore.BY_OPERATOR, op, "운영 복구", now)))
                .isEqualTo(1);
        assertThat(staffActions.find(operatorActionId).orElseThrow().restoredByName()).startsWith("운영");
        assertThat(staffActions.find(deleteId).orElseThrow().restored()).isFalse(); // 댓글 처리는 그대로

        jdbc.update("UPDATE posts SET hidden_at = now() WHERE id = ?", postId);
        assertThat(staffActions.find(hideId).orElseThrow().targetState(now())).isEqualTo("HIDDEN");
    }

    // ── 회원 쪽 판정 ────────────────────────────────────────────────────

    @Test
    void 탈퇴_진행_중_회원은_제재_대상이_아니다() {
        long user = member(false);
        jdbc.update("UPDATE users SET status = 'WITHDRAWING' WHERE id = ?", user);
        assertThat(sanctions.member(user)).hasValueSatisfying(m -> assertThat(m.gone()).isTrue());
        assertThat(boardSanctions.target(user)).hasValueSatisfying(t -> assertThat(t.gone()).isTrue());
        assertThat(sanctions.member(IDS.nextId())).isEmpty();
        assertThat(boardSanctions.target(IDS.nextId())).isEmpty();
    }
}
