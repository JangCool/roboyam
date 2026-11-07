package com.community.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.repository.master.AdminBoardSanctionStore;
import com.community.sanction.repository.master.AdminBoardStaffActionStore;
import com.community.sanction.repository.master.AdminSanctionStore;
import com.community.sanction.repository.master.BoardSanctionRepository;
import com.community.sanction.repository.master.BoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.BoardStaffActionRepository;
import com.community.sanction.repository.master.BoardStaffActionStore;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import com.community.sanction.repository.master.MemberSanctionRepository;
import com.community.sanction.repository.master.MemberSanctionStore;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import com.community.testsupport.BoardFixture;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MemberFixture;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 제재·게시판 제재·스텝 처리 목록 저장소(읽기만)가 JDBC 시절 SQL 과 같은 줄·같은 차례·같은 건수를 준다 — 같은 자료에 예전 문장을 돌려 견준다. 실제 PostgreSQL.
 * 시험 DB 를 다른 시험과 같이 쓰므로 거르기 없는 전체 건수도 그 순간의 예전 문장 결과와 견준다.
 */
class AdminSanctionStoresJpaTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(932);

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static MemberFixture members;
    private static BoardFixture boards;
    private static MemberSanctionStore sanctions;
    private static BoardSanctionStore boardSanctions;
    private static BoardStaffActionStore staffActions;
    private static AdminSanctionStore adminSanctions;
    private static AdminBoardSanctionStore adminBoardSanctions;
    private static AdminBoardStaffActionStore adminStaffActions;

    @BeforeAll
    static void setUp() {
        ApplicationContext context = JpaTestContext.context();
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        tx = new TransactionTemplate(JpaTestContext.transactions());
        members = new MemberFixture(context);
        boards = new BoardFixture(context);
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(JpaMasterConfig.ENTITY_MANAGER, EntityManagerFactory.class));
        sanctions = new MemberSanctionStore(JpaTestContext.query(), context.getBean(MemberSanctionRepository.class), em);
        staffActions = new BoardStaffActionStore(JpaTestContext.query(), context.getBean(BoardStaffActionRepository.class), em);
        boardSanctions = new BoardSanctionStore(JpaTestContext.query(), sanctions, context.getBean(BoardSanctionRepository.class),
                context.getBean(BoardStaffActionRepository.class), em);
        adminSanctions = new AdminSanctionStore(JpaTestContext.query(), sanctions);
        adminBoardSanctions = new AdminBoardSanctionStore(JpaTestContext.query(), boardSanctions);
        adminStaffActions = new AdminBoardStaffActionStore(JpaTestContext.query(), staffActions);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    private static long member(String nickname) {
        long id = IDS.nextId();
        members.user(id, "admin-sanction-" + id + "@test.local", true);
        members.profile(id, nickname);
        return id;
    }

    private static long operator() {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "aop-" + id + "@test.local", "관리" + Long.toString(id, 36));
        return id;
    }

    private static long board(String name) {
        long id = IDS.nextId();
        boards.board(id, "asb" + Long.toString(id, 36), name, 901);
        return id;
    }

    /** 닉네임 거르기 글자 — 서비스와 같은 탈출({@code \} 앞에 붙이기) */
    private static String like(String raw) {
        return "%" + raw.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static Object ts(Instant at) {
        return Timestamp.from(at);
    }

    // ── 사이트 제재 ─────────────────────────────────────────────────────

    @Test
    void 사이트_제재_목록_건수_회원_찾기_이의제기_셈이_예전_문장과_같다() {
        String tag = Long.toString(IDS.nextId(), 36).substring(5);
        long a = member("가_" + tag);
        long b = member("가x" + tag);
        long c = member("다" + tag);
        long op = operator();
        Instant now = now();
        Instant base = now.minus(Duration.ofHours(2));
        List<Long> ids = new ArrayList<>();
        inTx(() -> {
            long w1 = IDS.nextId();
            sanctions.insert(w1, a, SanctionKind.WARNING, 1, false, "경고", base, null, op, null, base);
            long s1 = IDS.nextId();
            sanctions.insert(s1, a, SanctionKind.SUSPEND_3, null, false, "정지", base.plusSeconds(1), base.plus(Duration.ofDays(3)), op, null, base.plusSeconds(1));
            long s2 = IDS.nextId();
            sanctions.insert(s2, b, SanctionKind.SUSPEND_7, null, false, "끝난 정지", base.minus(Duration.ofDays(8)), base.minus(Duration.ofDays(1)), op, null,
                    base.plusSeconds(2));
            long w2 = IDS.nextId();
            sanctions.insert(w2, c, SanctionKind.WARNING, 1, false, "경고", base.plusSeconds(3), null, op, null, base.plusSeconds(3));
            sanctions.voidSanction(w2, now);
            ids.addAll(List.of(w1, s1, s2, w2));
            sanctions.insertAppeal(IDS.nextId(), w1, a, "이의제기 사유 열 글자 넘게", base.plusSeconds(4));
            sanctions.insertAppeal(IDS.nextId(), s1, a, "이의제기 사유 열 글자 넘게 둘", base.plusSeconds(5));
            return null;
        });

        assertThat(adminSanctions.counts(now)).containsExactly((long[]) jdbc.queryForObject("SELECT (SELECT count(*) FROM member_sanctions), (SELECT count(*) FROM member_sanctions s "
                        + "WHERE s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND (s.ends_at IS NULL OR s.ends_at > ?)), (SELECT count(*) FROM member_sanctions s WHERE s.status <> 'ACTIVE')",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, ts(now)));
        assertThat(adminSanctions.running(now)).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM member_sanctions s WHERE s.status = 'ACTIVE' AND s.kind <> 'WARNING' "
                + "AND (s.ends_at IS NULL OR s.ends_at > ?)", Long.class, ts(now)));

        String select = "SELECT s.id FROM member_sanctions s LEFT JOIN profiles p ON p.user_id = s.user_id";
        String running = "s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND (s.ends_at IS NULL OR s.ends_at > ?)";
        record Case(AdminSanctionStore.Filter filter, String where, Object[] args) {
        }
        String nick = like("가_" + tag);
        List<Case> cases = List.of(
                new Case(new AdminSanctionStore.Filter(null, null, like(tag), null, null, null, null, false), "lower(p.nickname) LIKE ? ESCAPE '\\'",
                        new Object[]{like(tag)}),
                new Case(new AdminSanctionStore.Filter("ACTIVE", null, like(tag), null, null, null, null, true), running + " AND lower(p.nickname) LIKE ? ESCAPE '\\'",
                        new Object[]{ts(now), like(tag)}),
                new Case(new AdminSanctionStore.Filter("CLOSED", null, like(tag), null, null, null, null, false), "s.status <> 'ACTIVE' AND lower(p.nickname) LIKE ? ESCAPE '\\'",
                        new Object[]{like(tag)}),
                new Case(new AdminSanctionStore.Filter(null, SanctionKind.WARNING, nick, null, null, null, null, false),
                        "s.kind = 'WARNING' AND lower(p.nickname) LIKE ? ESCAPE '\\'", new Object[]{nick}),
                new Case(new AdminSanctionStore.Filter(null, null, null, null, base.plusSeconds(1), base.plusSeconds(2), a, false),
                        "s.created_at >= ? AND s.created_at <= ? AND s.user_id = ?", new Object[]{ts(base.plusSeconds(1)), ts(base.plusSeconds(2)), a}));
        for (Case k : cases) {
            String order = k.filter().oldest() ? " ORDER BY s.created_at, s.id" : " ORDER BY s.created_at DESC, s.id DESC";
            List<Long> expected = jdbc.queryForList(select + " WHERE " + k.where() + order, Long.class, k.args());
            assertThat(adminSanctions.page(k.filter(), now, 0, 50)).extracting(SanctionRow::id).as(k.where()).containsExactlyElementsOf(expected);
            assertThat(adminSanctions.total(k.filter(), now)).as(k.where()).isEqualTo(expected.size());
        }
        assertThat(adminSanctions.page(cases.get(0).filter(), now, 1, 2)).hasSize(2); // 넷 중 셋째·넷째
        // 밑줄은 글자 그대로 — 「가_」 는 「가x」 와 맞지 않는다
        assertThat(adminSanctions.page(cases.get(3).filter(), now, 0, 50)).extracting(SanctionRow::userId).containsOnly(a);
        long sanctionNo = sanctions.find(ids.get(1)).orElseThrow().no();
        assertThat(adminSanctions.page(new AdminSanctionStore.Filter(null, null, null, sanctionNo, null, null, null, false), now, 0, 5)).extracting(SanctionRow::id)
                .containsExactly(ids.get(1));
        assertThat(adminSanctions.byIds(ids)).containsOnlyKeys(ids);

        List<AdminSanctionStore.TargetRow> targets = adminSanctions.targets(like(tag), ("가_" + tag).toLowerCase(), 10);
        assertThat(targets).isEqualTo(jdbc.query("SELECT u.id, p.nickname, p.grade_key, u.status, "
                        + "(SELECT count(*) FROM member_sanctions s WHERE s.user_id = u.id AND s.kind = 'WARNING' AND s.status = 'ACTIVE') "
                        + "FROM profiles p JOIN users u ON u.id = p.user_id "
                        + "WHERE lower(p.nickname) LIKE ? ESCAPE '\\' AND u.deleted_at IS NULL AND u.status NOT IN ('WITHDRAWN', 'WITHDRAWING') "
                        + "ORDER BY (lower(p.nickname) = ?) DESC, char_length(p.nickname), p.nickname, u.id LIMIT ?",
                (rs, i) -> new AdminSanctionStore.TargetRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)), like(tag),
                ("가_" + tag).toLowerCase(), 10));
        assertThat(targets).first().satisfies(t -> {
            assertThat(t.id()).isEqualTo(a);
            assertThat(t.warningCount()).isEqualTo(1);
        });

        long[] appealCounts = new long[3];
        jdbc.query("SELECT status, count(*) FROM sanction_appeals GROUP BY status", rs -> {
            appealCounts[AppealStatus.valueOf(rs.getString(1)).ordinal()] = rs.getLong(2);
        });
        assertThat(adminSanctions.appealCounts()).containsExactly(appealCounts);
        assertThat(adminSanctions.pendingAppeals()).isEqualTo(appealCounts[0]);
        assertThat(adminSanctions.appealTotal(null)).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM sanction_appeals", Long.class));
        assertThat(adminSanctions.appealTotal(AppealStatus.PENDING)).isEqualTo(appealCounts[0]);
        assertThat(adminSanctions.appealPage(AppealStatus.PENDING, false, 0, 500)).extracting(AppealRow::id)
                .containsExactlyElementsOf(jdbc.queryForList("SELECT a.id FROM sanction_appeals a WHERE a.status = 'PENDING' ORDER BY a.created_at, a.id LIMIT 500", Long.class));
        assertThat(adminSanctions.appealPage(null, true, 0, 3)).extracting(AppealRow::id)
                .containsExactlyElementsOf(jdbc.queryForList("SELECT a.id FROM sanction_appeals a ORDER BY a.created_at DESC, a.id DESC LIMIT 3", Long.class));
        assertThat(adminSanctions.oldestPending(500)).isEqualTo(jdbc.query("SELECT a.id, p.nickname, coalesce(s.kind, 'BOARD_' || bs.kind), s.warning_no, a.created_at "
                        + "FROM sanction_appeals a LEFT JOIN member_sanctions s ON s.id = a.sanction_id LEFT JOIN board_sanctions bs ON bs.id = a.board_sanction_id "
                        + "LEFT JOIN profiles p ON p.user_id = a.user_id WHERE a.status = 'PENDING' ORDER BY a.created_at, a.id LIMIT ?",
                (rs, i) -> new AdminSanctionStore.PendingAppealRow(rs.getLong(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4, Integer.class),
                        rs.getTimestamp(5).toInstant()), 500));
    }

    // ── 게시판 제재 · 반복 검토 ─────────────────────────────────────────

    @Test
    void 게시판_제재_목록_회원_요약_반복_검토_쪽이_예전_문장과_같다() {
        String tag = Long.toString(IDS.nextId(), 36).substring(5);
        long board1 = board("가판 " + tag);
        long board2 = board("나판 " + tag);
        long manager = member("매" + tag);
        long target = member("대_" + tag);
        long op = operator();
        Instant now = now();
        Instant base = now.minus(Duration.ofHours(1));
        List<Long> ids = new ArrayList<>();
        inTx(() -> {
            for (int i = 0; i < 3; i++) {
                long id = IDS.nextId();
                boardSanctions.insert(id, board1, target, BoardSanctionKind.WARNING, "SPAM", null, manager, "MANAGER", null, null, null, null, base.plusSeconds(i), null,
                        base.plusSeconds(i));
                ids.add(id);
            }
            long restrict = IDS.nextId();
            boardSanctions.insert(restrict, board2, target, BoardSanctionKind.RESTRICT_3, "ABUSE", null, manager, "MANAGER", null, null, null, null, base,
                    base.plus(Duration.ofDays(3)), base.plusSeconds(10));
            ids.add(restrict);
            boardSanctions.voidSanction(ids.get(0), "OPERATOR", op, "무효", now);
            boardSanctions.openReview(IDS.nextId(), board1, target, 3, null, base.plusSeconds(20));
            return null;
        });

        assertThat(adminBoardSanctions.counts(now)).containsExactly((long[]) jdbc.queryForObject("SELECT (SELECT count(*) FROM board_sanctions), "
                        + "(SELECT count(*) FROM board_sanctions s WHERE s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND s.ends_at > ?), "
                        + "(SELECT count(*) FROM board_sanctions s WHERE s.status <> 'ACTIVE')",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, ts(now)));

        String select = "SELECT s.id FROM board_sanctions s LEFT JOIN profiles p ON p.user_id = s.user_id";
        record Case(AdminBoardSanctionStore.Filter filter, String where, Object[] args) {
        }
        String nick = like("대_" + tag);
        List<Case> cases = List.of(
                new Case(new AdminBoardSanctionStore.Filter(null, null, null, nick, null, null, null, null, false), "lower(p.nickname) LIKE ? ESCAPE '\\'",
                        new Object[]{nick}),
                new Case(new AdminBoardSanctionStore.Filter("ACTIVE", null, null, nick, null, null, null, null, true),
                        "s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND s.ends_at > ? AND lower(p.nickname) LIKE ? ESCAPE '\\'", new Object[]{ts(now), nick}),
                new Case(new AdminBoardSanctionStore.Filter("CLOSED", board1, null, nick, null, null, null, null, false),
                        "s.status <> 'ACTIVE' AND s.board_id = ? AND lower(p.nickname) LIKE ? ESCAPE '\\'", new Object[]{board1, nick}),
                new Case(new AdminBoardSanctionStore.Filter(null, null, BoardSanctionKind.WARNING, null, null, base.plusSeconds(1), base.plusSeconds(2), target, true),
                        "s.kind = 'WARNING' AND s.created_at >= ? AND s.created_at <= ? AND s.user_id = ?",
                        new Object[]{ts(base.plusSeconds(1)), ts(base.plusSeconds(2)), target}));
        for (Case k : cases) {
            String order = k.filter().oldest() ? " ORDER BY s.created_at, s.id" : " ORDER BY s.created_at DESC, s.id DESC";
            List<Long> expected = jdbc.queryForList(select + " WHERE " + k.where() + order, Long.class, k.args());
            assertThat(expected).as(k.where()).isNotEmpty();
            assertThat(adminBoardSanctions.page(k.filter(), now, 0, 50)).extracting(BoardSanctionRow::id).as(k.where()).containsExactlyElementsOf(expected);
            assertThat(adminBoardSanctions.total(k.filter(), now)).as(k.where()).isEqualTo(expected.size());
        }
        assertThat(adminBoardSanctions.boards()).extracting(AdminBoardSanctionStore.BoardRef::id).containsExactlyElementsOf(jdbc.queryForList(
                "SELECT b.id FROM boards b WHERE EXISTS (SELECT 1 FROM board_sanctions s WHERE s.board_id = b.id) ORDER BY b.name, b.id", Long.class));

        assertThat(adminBoardSanctions.memberBoards(target, now)).isEqualTo(jdbc.query("SELECT s.board_id, b.slug, b.name, b.name_en, "
                        + "count(*) FILTER (WHERE s.status <> 'VOIDED' AND s.kind = 'WARNING'), count(*) FILTER (WHERE s.status <> 'VOIDED' AND s.kind <> 'WARNING'), "
                        + "max(s.ends_at) FILTER (WHERE s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND s.ends_at > ?) "
                        + "FROM board_sanctions s JOIN boards b ON b.id = s.board_id WHERE s.user_id = ? GROUP BY s.board_id, b.slug, b.name, b.name_en ORDER BY b.name, s.board_id",
                (rs, i) -> new AdminBoardSanctionStore.MemberBoardRow(new AdminBoardSanctionStore.BoardRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                        rs.getLong(5), rs.getLong(6), rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()), ts(now), target));
        assertThat(adminBoardSanctions.memberBoards(target, now)).extracting(AdminBoardSanctionStore.MemberBoardRow::warnings).containsExactly(2L, 0L);

        assertThat(adminBoardSanctions.reviewCounts()).containsExactly((long[]) jdbc.queryForObject("SELECT count(*) FILTER (WHERE status = 'OPEN'), "
                + "count(*) FILTER (WHERE status = 'CLOSED') FROM board_sanction_reviews", (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}));
        assertThat(adminBoardSanctions.openReviews()).isEqualTo(adminBoardSanctions.reviewCounts()[0]);
        List<AdminBoardSanctionStore.ReviewLine> lines = adminBoardSanctions.reviewPage(true, now, 0, 500);
        assertThat(lines).extracting(l -> l.row().id()).containsExactlyElementsOf(jdbc.queryForList(
                "SELECT r.id FROM board_sanction_reviews r WHERE r.status = 'OPEN' ORDER BY r.opened_at, r.id LIMIT 500", Long.class));
        AdminBoardSanctionStore.ReviewLine mine = lines.stream().filter(l -> l.row().userId() == target).findFirst().orElseThrow();
        assertThat(mine.recentCount()).isEqualTo(2); // 7일 안 · 무효 제외 · 그 게시판
        assertThat(mine.recentBoardCount()).isEqualTo(2);
        assertThat(adminBoardSanctions.reviewPage(false, now, 0, 3)).extracting(l -> l.row().id()).containsExactlyElementsOf(jdbc.queryForList(
                "SELECT r.id FROM board_sanction_reviews r WHERE r.status = 'CLOSED' ORDER BY r.closed_at DESC, r.id DESC LIMIT 3", Long.class));
    }

    // ── 스텝 처리 감독 ──────────────────────────────────────────────────

    @Test
    void 스텝_처리_감독_목록이_예전_문장과_같다() {
        String tag = Long.toString(IDS.nextId(), 36).substring(5);
        long board = board("감독판 " + tag);
        long staff = member("스_" + tag);
        long other = member("스x" + tag);
        long author = member("작" + tag);
        long postId = IDS.nextId();
        boards.post(postId, board, author, "글", "본문");
        long commentId = IDS.nextId();
        boards.comment(commentId, postId, board, author, "댓글");
        Instant now = now();
        Instant base = now.minus(Duration.ofMinutes(30));
        inTx(() -> {
            staffActions.insert(IDS.nextId(), board, staff, "STAFF", null, "POST", "HIDE", author, postId, null, "SPAM", "글", null, "작", base);
            staffActions.insert(IDS.nextId(), board, other, "STAFF", null, "POST", "DELETE", author, postId, null, "SPAM", "글", null, "작", base.plusSeconds(1));
            long restored = IDS.nextId();
            staffActions.insert(restored, board, staff, "STAFF", null, "COMMENT", "DELETE", author, postId, commentId, "ABUSE", "글", "댓글", "작", base.plusSeconds(2));
            staffActions.markRestored(restored, "MANAGER", other, "되돌림", now);
            return null;
        });

        String select = "SELECT a.id FROM board_staff_actions a LEFT JOIN profiles ap ON ap.user_id = a.actor_id";
        record Case(AdminBoardStaffActionStore.Filter filter, String where, Object[] args) {
        }
        String nick = like("스_" + tag);
        List<Case> cases = List.of(
                new Case(new AdminBoardStaffActionStore.Filter(board, null, null, null, null, null), "a.kind <> 'SANCTION' AND a.board_id = ?", new Object[]{board}),
                new Case(new AdminBoardStaffActionStore.Filter(board, "POST_DELETE", null, null, null, null), "a.kind <> 'SANCTION' AND a.board_id = ? AND a.kind = 'POST' "
                        + "AND a.action = 'DELETE'", new Object[]{board}),
                new Case(new AdminBoardStaffActionStore.Filter(board, null, "RESTORED", null, null, null), "a.kind <> 'SANCTION' AND a.board_id = ? AND a.restored_at IS NOT NULL",
                        new Object[]{board}),
                new Case(new AdminBoardStaffActionStore.Filter(board, null, "OPEN", null, base, base.plusSeconds(1)), "a.kind <> 'SANCTION' AND a.board_id = ? "
                        + "AND a.restored_at IS NULL AND a.created_at >= ? AND a.created_at <= ?", new Object[]{board, ts(base), ts(base.plusSeconds(1))}),
                // 처리자 닉네임 — 밑줄은 글자 그대로(「스_」 는 「스x」 와 맞지 않는다)
                new Case(new AdminBoardStaffActionStore.Filter(null, null, null, nick, null, null), "a.kind <> 'SANCTION' AND lower(ap.nickname) LIKE ? ESCAPE '\\'",
                        new Object[]{nick}));
        for (Case k : cases) {
            List<Long> expected = jdbc.queryForList(select + " WHERE " + k.where() + " ORDER BY a.created_at DESC, a.id DESC", Long.class, k.args());
            assertThat(expected).as(k.where()).isNotEmpty();
            assertThat(adminStaffActions.page(k.filter(), 0, 50)).extracting(ActionRow::id).as(k.where()).containsExactlyElementsOf(expected);
            assertThat(adminStaffActions.total(k.filter())).as(k.where()).isEqualTo(expected.size());
        }
        assertThat(adminStaffActions.page(cases.get(4).filter(), 0, 50)).extracting(ActionRow::actorId).containsOnly(staff);
        assertThat(adminStaffActions.boards()).extracting(AdminBoardSanctionStore.BoardRef::id).containsExactlyElementsOf(jdbc.queryForList(
                "SELECT b.id FROM boards b WHERE EXISTS (SELECT 1 FROM board_staff_actions a WHERE a.board_id = b.id AND a.kind <> 'SANCTION') ORDER BY b.name, b.id",
                Long.class));
    }
}
