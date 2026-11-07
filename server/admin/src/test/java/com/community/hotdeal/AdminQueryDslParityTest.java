package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.address.entity.AddressImportPhase;
import com.community.address.repository.master.AddressDbLoadQueries;
import com.community.audit.repository.master.AuditRetentionStore;
import com.community.board.repository.master.BoardContentQueries;
import com.community.dashboard.dto.response.gadget.DashboardGroupRequestsView;
import com.community.dashboard.dto.response.gadget.DashboardHotdealStatusView;
import com.community.dashboard.dto.response.gadget.DashboardHotdealTopView;
import com.community.dashboard.dto.response.gadget.DashboardPointShopView;
import com.community.dashboard.dto.response.gadget.DashboardRecentSignupsView;
import com.community.dashboard.dto.response.gadget.DashboardReportsView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.hotdeal.repository.master.AdminDealQueries;
import com.community.member.repository.master.MemberSettingsQueries;
import com.community.withdrawal.repository.master.MemberWithdrawalAdminStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 네이티브 SQL → QueryDSL 전환(1단계, 관리자)의 결과가 같은지 — 예전 문장을 그대로 시험에 두고 같은 DB 에서 새 질의와 견준다(실제 PostgreSQL).
 * 시험 DB 는 다른 시험과 같이 쓰므로 남은 데이터 위에 이 시험이 만든 줄(게시판·글·딜·신고 사건·차단)을 얹어 견준다.
 */
class AdminQueryDslParityTest extends HotdealAdminTestBase {

    private static final String ALIVE_POST = "p.deleted_at IS NULL AND p.status = 'PUBLISHED' AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > ?)";
    private static final String PUBLIC_POST = ALIVE_POST + " AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= ?)) AND p.group_id IS NULL";
    private static final String LIVE_DEAL = "d.status = 'ACTIVE' AND (d.ends_at IS NULL OR d.ends_at > ?) AND (d.ends_at IS NOT NULL OR ? = 0 OR d.created_at > ?)";

    @Autowired
    private DashboardGadgetStore gadgets;
    @Autowired
    private AdminDealQueries deals;
    @Autowired
    private BoardContentQueries boardContent;
    @Autowired
    private MemberSettingsQueries memberSettings;
    @Autowired
    private AuditRetentionStore auditRetention;
    @Autowired
    private DashboardAnalyticsStore analyticsStore;
    @Autowired
    private MemberWithdrawalAdminStore withdrawals;
    @Autowired
    private AddressDbLoadQueries addressLoads;
    @Autowired
    private PlatformTransactionManager transactions;

    private final List<Long> cases = new ArrayList<>();
    private final List<Long> loads = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : cases) {
            jdbc.update("DELETE FROM reports WHERE case_id = ?", id);
            jdbc.update("DELETE FROM report_cases WHERE id = ?", id);
        }
        for (Long id : loads) {
            jdbc.update("DELETE FROM address_db_loads WHERE id = ?", id);
        }
    }

    // ── 대시보드 가젯 ────────────────────────────────────────────────────────

    @Test
    void 대시보드_가젯_질의는_예전_네이티브_문장과_결과가_같다() {
        long board = dealBoard();
        long author = newMember();
        long a = deal(board, author, domain("a"));
        long b = deal(board, author, domain("b"));
        flag(b);
        long c = deal(board, author, domain("c"));
        endByAuthor(c);
        jdbc.update("UPDATE posts SET like_count = 7 WHERE id = ?", a);
        long pending = reportCase(a, "PENDING", Instant.parse("2000-01-01T00:00:00Z"));
        report(pending, a, board, "SPAM_AD");
        report(pending, a, board, "ABUSE");
        report(pending, a, board, "ABUSE");
        report(pending, a, board, "SPAM_AD");
        jdbc.update("INSERT INTO report_events (id, case_id, type, actor_kind, created_at) VALUES (?, ?, 'AUTO_HIDDEN', 'SYSTEM', now())", ids.nextId(), pending);
        reportCase(b, "IN_REVIEW", Instant.parse("2000-01-02T00:00:00Z"));

        long[] counts = gadgets.reportCounts();
        assertThat(counts).isEqualTo(jdbc.queryForObject("SELECT count(*) FILTER (WHERE status = 'PENDING'), count(*) FILTER (WHERE status = 'IN_REVIEW') "
                + "FROM report_cases WHERE status IN ('PENDING', 'IN_REVIEW')", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}));

        List<DashboardReportsView.Oldest> oldest = gadgets.oldestPendingReports(50);
        assertThat(oldest).isEqualTo(jdbc.query("SELECT c.id, c.target_type, c.first_reported_at, "
                        + "(SELECT r.reason FROM reports r WHERE r.case_id = c.id GROUP BY r.reason ORDER BY count(*) DESC, r.reason LIMIT 1), "
                        + "EXISTS (SELECT 1 FROM report_events e WHERE e.case_id = c.id AND e.type = 'AUTO_HIDDEN') "
                        + "FROM report_cases c WHERE c.status = 'PENDING' ORDER BY c.first_reported_at, c.id LIMIT ?",
                (rs, i) -> new DashboardReportsView.Oldest(Long.toString(rs.getLong(1)), rs.getString(4) == null ? "OTHER" : rs.getString(4), rs.getString(2),
                        instant(rs, 3), rs.getBoolean(5)), 50));
        assertThat(oldest.get(0)).isEqualTo(new DashboardReportsView.Oldest(Long.toString(pending), "ABUSE", "POST", Instant.parse("2000-01-01T00:00:00Z"), true));

        assertThat(gadgets.recentPostIds(20)).isEqualTo(jdbc.queryForList("SELECT id FROM posts WHERE deleted_at IS NULL AND status = 'PUBLISHED' ORDER BY id DESC LIMIT 20",
                Long.class));
        assertThat(gadgets.openlyReportedPosts(List.of(a, b, c))).containsExactlyInAnyOrder(a, b);

        assertThat(gadgets.pendingGroups()).isEqualTo(count("SELECT count(*) FROM groups WHERE status = 'PENDING'"));
        assertThat(gadgets.recentGroupRequests(10)).isEqualTo(jdbc.query("SELECT g.id, g.name, p.nickname, g.requested_at FROM groups g "
                        + "LEFT JOIN profiles p ON p.user_id = coalesce(g.creator_id, g.leader_id) WHERE g.status = 'PENDING' ORDER BY g.requested_at DESC, g.id DESC LIMIT 10",
                (rs, i) -> new DashboardGroupRequestsView.Recent(Long.toString(rs.getLong(1)), rs.getString(2), nz(rs.getString(3)), instant(rs, 4))));

        assertThat(gadgets.recentSignups(10)).isEqualTo(jdbc.query("SELECT u.id, p.nickname, CASE WHEN u.password_hash IS NOT NULL THEN 'EMAIL' ELSE "
                        + "(SELECT i.provider FROM user_identities i WHERE i.user_id = u.id ORDER BY i.created_at, i.id LIMIT 1) END, u.created_at "
                        + "FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE u.status <> 'WITHDRAWN' ORDER BY u.created_at DESC, u.id DESC LIMIT 10",
                (rs, i) -> new DashboardRecentSignupsView.Item(Long.toString(rs.getLong(1)), nz(rs.getString(2)), rs.getString(3) == null ? "EMAIL" : rs.getString(3),
                        instant(rs, 4))));

        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(30));
        assertThat(gadgets.memberStats(from, now)).isEqualTo(jdbc.queryForObject("SELECT (SELECT count(*) FROM users WHERE created_at >= ? AND created_at < ?), "
                        + "(SELECT count(*) FROM users WHERE status = 'WITHDRAWN' AND deleted_at >= ? AND deleted_at < ?), "
                        + "(SELECT count(*) FROM users WHERE status IN ('SUSPENDED', 'BANNED')), "
                        + "(SELECT count(*) FROM member_withdrawals WHERE status = 'SCHEDULED')",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, ts(from), ts(now), ts(from), ts(now)));

        assertThat(gadgets.pendingNicknameImages()).isEqualTo(count("SELECT count(*) FROM nickname_image_requests WHERE status = 'PENDING'"));
        assertThat(gadgets.oldestNicknameImages(10)).isEqualTo(jdbc.query("SELECT r.id, p.nickname, coalesce(r.still_key, r.image_key) FROM nickname_image_requests r "
                        + "LEFT JOIN profiles p ON p.user_id = r.user_id WHERE r.status = 'PENDING' ORDER BY r.id LIMIT 10",
                (rs, i) -> new DashboardGadgetStore.NicknameImageRow(rs.getLong(1), nz(rs.getString(2)), rs.getString(3))));

        for (int days : new int[] {0, 30}) {
            Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);
            Instant tomorrow = todayStart.plus(Duration.ofDays(1));
            OffsetDateTime n = ts(now);
            OffsetDateTime expireFrom = ts(now.minus(Duration.ofDays(days)));
            long todayId = SnowflakeIdGenerator.floorIdAt(todayStart);
            assertThat(gadgets.hotdealStatus(now, todayStart, tomorrow, days)).isEqualTo(jdbc.queryForObject("SELECT count(*) FILTER (WHERE " + LIVE_DEAL + "), "
                            + "count(*) FILTER (WHERE " + LIVE_DEAL + " AND d.flagged_at IS NOT NULL), "
                            + "count(*) FILTER (WHERE " + LIVE_DEAL + " AND d.ends_at < ?), "
                            + "count(*) FILTER (WHERE d.post_id >= ?) "
                            + "FROM post_deals d JOIN posts p ON p.id = d.post_id WHERE (d.status = 'ACTIVE' OR d.post_id >= ?) AND " + ALIVE_POST,
                    (rs, i) -> new DashboardHotdealStatusView(rs.getLong(1), rs.getLong(4), rs.getLong(3), rs.getLong(2)),
                    n, days, expireFrom, n, days, expireFrom, n, days, expireFrom, ts(tomorrow), todayId, todayId, n));
            long fromId = SnowflakeIdGenerator.floorIdAt(now.minus(Duration.ofDays(7)));
            List<DashboardHotdealTopView.Item> top = gadgets.hotdealTop(now, days, 10);
            assertThat(top).isEqualTo(jdbc.query("SELECT d.post_id, d.store_name, p.title, p.like_count, d.deal_price FROM post_deals d JOIN posts p ON p.id = d.post_id "
                            + "WHERE d.post_id >= ? AND " + LIVE_DEAL + " AND " + PUBLIC_POST + " ORDER BY p.like_count DESC, d.post_id DESC LIMIT ?",
                    (rs, i) -> new DashboardHotdealTopView.Item(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3), rs.getInt(4), (Long) rs.getObject(5)),
                    fromId, n, days, expireFrom, n, n, 10));
        }

        Instant longAgo = Instant.parse("2000-01-01T00:00:00Z");
        Instant later = now.plus(Duration.ofDays(400));
        assertThat(gadgets.pointFlow(longAgo, later)).isEqualTo(jdbc.queryForObject("SELECT coalesce(sum(delta) FILTER (WHERE kind = 'EARN'), 0), "
                + "coalesce(-sum(delta) FILTER (WHERE kind = 'USE'), 0) FROM point_ledger WHERE created_at >= ? AND created_at < ?", (rs, i) -> new long[] {rs.getLong(1),
                rs.getLong(2)}, ts(longAgo), ts(later)));
        assertThat(gadgets.pointsExpiring(longAgo, later)).isEqualTo(count("SELECT coalesce(sum(remaining_amount), 0) FROM point_lots WHERE kind = 'FREE' "
                + "AND remaining_amount > 0 AND expires_at >= '2000-01-01' AND expires_at < now() + interval '400 days'"));
        assertThat(gadgets.pointShopPurchasesSince(longAgo)).isEqualTo(count("SELECT count(*) FROM point_shop_purchases WHERE id >= " + SnowflakeIdGenerator.floorIdAt(longAgo)));
        assertThat(gadgets.lowStockItems(now, 1000, 10)).isEqualTo(jdbc.query("SELECT id, name, name_en, stock FROM point_shop_items WHERE active AND stock IS NOT NULL "
                        + "AND stock <= ? AND (sale_start_at IS NULL OR sale_start_at <= ?) AND (sale_end_at IS NULL OR sale_end_at > ?) ORDER BY stock, sort_order, id LIMIT ?",
                (rs, i) -> new DashboardPointShopView.LowStock(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3), rs.getInt(4)), 1000, ts(now), ts(now), 10));
        assertThat(gadgets.shopSales(longAgo, later)).containsExactlyInAnyOrderElementsOf(jdbc.query("SELECT id, name, name_en, sale_start_at, sale_end_at "
                        + "FROM point_shop_items WHERE active AND ((sale_start_at >= ? AND sale_start_at < ?) OR (sale_end_at >= ? AND sale_end_at < ?))",
                (rs, i) -> new DashboardGadgetStore.ShopSale(rs.getLong(1), rs.getString(2), rs.getString(3), instant(rs, 4), instant(rs, 5)), ts(longAgo), ts(later),
                ts(longAgo), ts(later)));

        assertThat(gadgets.failedBatchRuns(longAgo, 10)).isEqualTo(jdbc.query("SELECT id, job_key, coalesce(finished_at, started_at), error_message, message_key, "
                        + "message_args FROM batch_runs WHERE started_at >= ? AND status = 'FAIL' ORDER BY started_at DESC, id DESC LIMIT 10",
                (rs, i) -> new DashboardGadgetStore.BatchRun(rs.getLong(1), rs.getString(2), instant(rs, 3), rs.getString(4), rs.getString(5), rs.getString(6)),
                ts(longAgo)));
        assertThat(gadgets.runningBatchRuns(10)).isEqualTo(jdbc.query("SELECT id, job_key, started_at FROM batch_runs WHERE status = 'RUNNING' ORDER BY started_at, id LIMIT 10",
                (rs, i) -> new DashboardGadgetStore.BatchRun(rs.getLong(1), rs.getString(2), instant(rs, 3), null, null, null)));
        assertThat(gadgets.recentOperatorAudits(10)).isEqualTo(jdbc.query("SELECT a.id, o.operator_name, a.action, a.occurred_at FROM audit_logs a "
                        + "LEFT JOIN operators o ON o.id = a.actor_id WHERE a.actor_type = 'OPERATOR' AND a.result = 'SUCCESS' ORDER BY a.occurred_at DESC, a.id DESC LIMIT 10",
                (rs, i) -> new DashboardGadgetStore.AuditRow(rs.getLong(1), rs.getString(2), rs.getString(3), instant(rs, 4))));
    }

    // ── 핫딜 관리 ───────────────────────────────────────────────────────────

    @Test
    void 핫딜_관리_질의는_딜_상태와_판매처를_예전과_같이_센다() {
        long board = dealBoard();
        long author = newMember();
        long other = newMember();
        String host = domain("s");
        long a = deal(board, author, host);
        long b = deal(board, author, host);
        long c = deal(board, other, host);
        long d = deal(board, author, domain("t"));
        flag(b);
        expire(d);
        jdbc.update("UPDATE post_deals SET final_host = ? WHERE post_id = ?", "f." + host, c);
        Instant now = Instant.now();

        AdminDealQueries.Filter all = new AdminDealQueries.Filter(List.of(board), null, null, null, null);
        Map<String, Long> counts = deals.counts(all, null, 30, now);
        assertThat(counts).containsExactly(Map.entry("ACTIVE", 2L), Map.entry("FLAGGED", 1L), Map.entry("ENDED", 0L), Map.entry("EXPIRED", 1L),
                Map.entry(AdminDealQueries.ALL, 4L));
        assertThat(deals.search(all, "ACTIVE", null, 30, now, 0, 10).rows()).extracting(AdminDealQueries.Row::postId).containsExactly(c, a);
        assertThat(deals.search(all, "ALL", List.of(host), 30, now, 0, 10).total()).isEqualTo(3); // a·b(의심도 진행 중)·c — d 는 기간 종료
        assertThat(deals.search(all, "ALL", List.of(), 30, now, 0, 10).total()).isZero();
        assertThat(deals.counts(new AdminDealQueries.Filter(List.of(), null, null, null, null), null, 30, now).values()).containsOnly(0L);
        assertThat(deals.blockedActiveCount(all, List.of("f." + host), 30, now)).isEqualTo(1);
        assertThat(deals.row(a, List.of(board), now)).satisfies(r -> {
            assertThat(r.postId()).isEqualTo(a);
            assertThat(r.publicPost()).isTrue();
        });
        assertThat(deals.activeHosts(List.of(board))).containsExactlyInAnyOrder(host, "f." + host, jdbc.queryForObject("SELECT product_host FROM post_deals WHERE post_id = ?",
                String.class, d));
        assertThat(deals.postBoards(List.of(a, b))).containsOnly(Map.entry(a, board), Map.entry(b, board));

        String keyA = jdbc.queryForObject("SELECT product_url_key FROM post_deals WHERE post_id = ?", String.class, a);
        jdbc.update("UPDATE post_deals SET product_url_key = ? WHERE post_id = ?", keyA, c);
        assertThat(deals.sameDeals(keyA, a, 30, now, 10)).extracting(AdminDealQueries.SameRow::postId).containsExactly(c);

        AdminDealQueries.DomainImpact impact = deals.domainImpact(host, 30, now, 10);
        assertThat(impact.count()).isEqualTo(3);
        assertThat(impact.samples()).extracting(AdminDealQueries.LiveDeal::postId).containsExactly(c, b, a);
        assertThat(deals.liveDeals(30, now)).extracting(AdminDealQueries.LiveDeal::postId).contains(a, b, c).doesNotContain(d);

        assertThat(deals.otherMembers(host, author, 7, now)).isEqualTo(0); // other 의 글은 최종 호스트가 달라 판매처가 다르다
        assertThat(deals.otherMembers("f." + host, author, 7, now)).isEqualTo(1);
        Map<Long, long[]> members = deals.storeMembers(host, List.of(author, other), 7, now);
        assertThat(members.keySet()).containsExactly(author, other);
        assertThat(members.get(author)).containsExactly(2, 1);
        assertThat(members.get(other)).containsExactly(0, 1);
        assertThat(deals.suspicionPosts(List.of(a, b, c), 2)).extracting(AdminDealQueries.SuspicionPost::postId).containsExactly(c, b);
    }

    // ── 게시판 관리 ─────────────────────────────────────────────────────────

    @Test
    void 게시판_글_집계와_일괄_수정_삭제는_예전과_같다() {
        long board = generalBoard();
        long author = newMember();
        long prefix = ids.nextId();
        jdbc.update("INSERT INTO board_prefixes (id, board_id, name, sort_order) VALUES (?, ?, '질문', 1)", prefix, board);
        long p1 = boardPost(board, author, "하나");
        long p2 = boardPost(board, author, "둘");
        boardPost(board, author, "셋");
        jdbc.update("UPDATE posts SET prefix_id = ? WHERE id IN (?, ?)", prefix, p1, p2);
        jdbc.update("UPDATE posts SET deleted_at = now() WHERE id = ?", p2);

        Map<Long, Long> expected = new HashMap<>();
        jdbc.query("SELECT board_id, count(*) FROM posts WHERE deleted_at IS NULL AND status = 'PUBLISHED' GROUP BY board_id", rs -> {
            expected.put(rs.getLong(1), rs.getLong(2));
        });
        assertThat(boardContent.postCounts()).isEqualTo(expected);
        assertThat(boardContent.postCount(board)).isEqualTo(2);
        assertThat(boardContent.hasLivePosts(board)).isTrue();
        assertThat(boardContent.prefixUsage(board)).containsOnly(Map.entry(prefix, 2L));

        TransactionTemplate tx = new TransactionTemplate(transactions);
        Integer first = tx.execute(s -> boardContent.markAdult(board, List.of(prefix)));
        Integer again = tx.execute(s -> boardContent.markAdult(board, List.of(prefix)));
        assertThat(first).isEqualTo(2);
        assertThat(again).isZero();
        assertThat(count("SELECT count(*) FROM posts WHERE board_id = " + board + " AND adult")).isEqualTo(2);

        tx.executeWithoutResult(s -> boardContent.deleteContent(board));
        assertThat(count("SELECT count(*) FROM posts WHERE board_id = " + board)).isZero();
        assertThat(boardContent.hasLivePosts(board)).isFalse();
    }

    // ── 회원 상세·이름 찾기·감사·주소 적재 ───────────────────────────────────

    @Test
    void 회원_설정_건수와_이름_찾기는_예전과_같다() {
        long user = newMember();
        long target = newMember();
        long gone = withdrawnMember(Instant.now().minus(Duration.ofDays(1)));
        jdbc.update("INSERT INTO user_blocks (id, user_id, target_id, created_at) VALUES (?, ?, ?, now()), (?, ?, ?, now())", ids.nextId(), user, target, ids.nextId(), user, gone);
        jdbc.update("INSERT INTO user_memos (id, user_id, target_id, memo, created_at, updated_at) VALUES (?, ?, ?, '메모', now(), now())", ids.nextId(), user, target);
        jdbc.update("INSERT INTO blocked_words (id, user_id, word, created_at) VALUES (?, ?, '금칙', now())", ids.nextId(), user);

        MemberSettingsQueries.Counts counts = new TransactionTemplate(transactions).execute(s -> memberSettings.countsOf(user));
        assertThat(counts).isEqualTo(jdbc.queryForObject("""
                SELECT coalesce((SELECT accept_dm FROM dm_user_states WHERE user_id = ?), true),
                       (SELECT count(*) FROM user_blocks b JOIN users u ON u.id = b.target_id WHERE b.user_id = ? AND u.status <> 'WITHDRAWN' AND u.deleted_at IS NULL),
                       (SELECT count(*) FROM blocked_words WHERE user_id = ?),
                       (SELECT count(*) FROM user_memos m JOIN users u ON u.id = m.target_id WHERE m.user_id = ? AND u.status <> 'WITHDRAWN' AND u.deleted_at IS NULL),
                       (SELECT count(*) FROM member_notifications WHERE user_id = ?),
                       (SELECT count(*) FROM member_notifications WHERE user_id = ? AND read_at IS NULL)""",
                (rs, i) -> new MemberSettingsQueries.Counts(rs.getBoolean(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)),
                user, user, user, user, user, user));
        assertThat(counts.blockedUsers()).isEqualTo(1);
        assertThat(counts.acceptDm()).isTrue();

        long operator = newOperatorRow();
        Map<Long, String> nickname = new LinkedHashMap<>();
        nickname.put(user, jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, user));
        assertThat(withdrawals.nicknames(List.of(user))).isEqualTo(nickname);
        assertThat(withdrawals.operatorNames(List.of(operator, -1L))).containsOnly(Map.entry(operator, operatorName(operator)));
        assertThat(withdrawals.groupNames(List.of(-1L))).isEmpty();

        long board = generalBoard();
        long post = boardPost(board, user, "제목 하나");
        assertThat(analyticsStore.postTitles(List.of(post, -1L))).containsOnly(Map.entry(post, new DashboardAnalyticsStore.Name("제목 하나", null)));
        Set<Long> items = new HashSet<>(jdbc.queryForList("SELECT id FROM point_shop_items LIMIT 3", Long.class));
        Map<Long, DashboardAnalyticsStore.Name> itemNames = new HashMap<>();
        jdbc.query("SELECT id, name, name_en FROM point_shop_items WHERE id = ANY (?::bigint[])", rs -> {
            itemNames.put(rs.getLong(1), new DashboardAnalyticsStore.Name(rs.getString(2), rs.getString(3)));
        }, "{" + String.join(",", items.stream().map(String::valueOf).toList()) + "}");
        assertThat(analyticsStore.pointShopNames(items)).isEqualTo(itemNames);

        List<AuditRetentionStore.LastChange> last = jdbc.query("SELECT occurred_at, actor_id FROM audit_logs WHERE action = 'AUDIT_RETENTION_UPDATE' AND result = 'SUCCESS' "
                + "ORDER BY occurred_at DESC, id DESC LIMIT 1", (rs, i) -> new AuditRetentionStore.LastChange(rs.getTimestamp(1).toInstant(), (Long) rs.getObject(2)));
        assertThat(auditRetention.lastChange()).isEqualTo(last.stream().findFirst());
    }

    @Test
    void 주소_적재_단계는_진행_중인_줄만_바꾼다() {
        long running = load("RUNNING");
        long done = load("DONE");
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Long changed = tx.execute(s -> addressLoads.markPhase(running, AddressImportPhase.MERGING));
        Long unchanged = tx.execute(s -> addressLoads.markPhase(done, AddressImportPhase.MERGING));
        assertThat(changed).isEqualTo(1);
        assertThat(unchanged).isZero();
        assertThat(jdbc.queryForObject("SELECT phase FROM address_db_loads WHERE id = ?", String.class, running)).isEqualTo("MERGING");
        assertThat(jdbc.queryForObject("SELECT phase FROM address_db_loads WHERE id = ?", String.class, done)).isEqualTo("DONE");
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private long reportCase(long postId, String status, Instant firstAt) {
        long id = ids.nextId();
        long board = jdbc.queryForObject("SELECT board_id FROM posts WHERE id = ?", Long.class, postId);
        jdbc.update("INSERT INTO report_cases (id, target_type, target_id, post_id, board_id, status, first_reported_at, last_reported_at) VALUES (?, 'POST', ?, ?, ?, ?, ?, ?)",
                id, postId, postId, board, status, ts(firstAt), ts(firstAt));
        cases.add(id);
        return id;
    }

    private void report(long caseId, long postId, long boardId, String reason) {
        jdbc.update("INSERT INTO reports (id, case_id, target_type, target_id, post_id, board_id, reporter_id, reason, counted) VALUES (?, ?, 'POST', ?, ?, ?, ?, ?, true)",
                ids.nextId(), caseId, postId, postId, boardId, newMember(), reason);
    }

    private long load(String status) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO address_db_loads (id, status, phase, file_names, charset, node_id, started_at, finished_at) VALUES (?, ?, ?, 'a.txt', 'UTF-8', 0, now(), "
                + (status.equals("RUNNING") ? "NULL" : "now()") + ")", id, status, status.equals("RUNNING") ? "PARSING" : "DONE");
        loads.add(id);
        return id;
    }

    private long newOperatorRow() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())",
                id, "parity-" + id + "@test.local", "p" + (id % 10_000_000_000_000L));
        return id;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }


    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
