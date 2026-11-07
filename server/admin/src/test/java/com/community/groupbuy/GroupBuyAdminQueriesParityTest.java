package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.repository.master.CommerceGroupBuySettingsQueries;
import com.community.groupbuy.repository.master.GroupBuyAdminQueries;
import com.community.groupbuy.repository.master.GroupBuyOrderAdminQueries;
import com.community.groupbuy.repository.master.GroupBuyProductAdminQueries;
import com.community.groupbuy.repository.master.GroupBuyRequestAdminQueries;
import com.community.groupbuy.repository.master.GroupBuyRowNames;
import com.community.groupbuy.service.GroupBuyBusinessDays;
import com.community.holiday.service.BusinessDayCalendar;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestWiring;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.community.zconfiguration.time.ServiceTime;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 관리자 공구 화면 저장소(3단계 2차 — Facade 안의 SQL 을 QueryDSL 로 옮긴 것)가 <b>예전 SQL 과 같은 결과</b>(건수·차례·쪽)를 내는지 실제 PostgreSQL 에서 견준다
 * ({@link CommerceTestDatabase}, commerce.md 18절). 예전 문장은 옮기기 전 Facade 의 것을 그대로 적었다(예전에 메모리에서 거르고 나누던 요청 대기열 「기한 경고만」·대시보드 기한 단계는
 * 예전 자바 셈 그대로).
 *
 * <p>시험마다 연결 하나를 트랜잭션으로 열어 행을 넣고 끝나면 되돌린다 — JPA 문맥도 그 연결을 쓴다. 다른 시험이 남긴 행도 함께 읽으므로 거르지 않는 질의는 표 전체를 견준다.
 */
class GroupBuyAdminQueriesParityTest {

    private static final CurrentConnection CURRENT = new CurrentConnection();
    private static AnnotationConfigApplicationContext jpa;

    private Connection connection;
    private JdbcTemplate jdbc;
    private JPAQueryFactory query;

    @Configuration(proxyBeanMethods = false)
    @Import(JpaTestWiring.class)
    static class JpaWiring {
        @Bean
        DataSource dataSource() {
            return CURRENT;
        }
    }

    /** 지금 시험의 연결 하나를 돌려주는 데이터소스(닫아도 닫히지 않는다) */
    static final class CurrentConnection extends AbstractDataSource {
        volatile SingleConnectionDataSource single;

        @Override
        public Connection getConnection() throws SQLException {
            return single.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }

    @BeforeEach
    void open() throws Exception {
        connection = CommerceTestDatabase.migrated("admin").getConnection();
        connection.setAutoCommit(false);
        SingleConnectionDataSource single = new SingleConnectionDataSource(connection, true);
        CURRENT.single = single;
        if (jpa == null) {
            jpa = new AnnotationConfigApplicationContext(JpaWiring.class);
        }
        jdbc = new JdbcTemplate(single);
        query = jpa.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
    }

    @AfterEach
    void close() throws Exception {
        connection.rollback();
        connection.close();
    }

    @AfterAll
    static void closeJpa() {
        if (jpa != null) {
            jpa.close();
            jpa = null;
        }
    }

    // ── 공구 목록 ───────────────────────────────────────────────────────────

    @Test
    void 공구_목록의_탭_수_건수_쪽이_예전_SQL_과_같다() {
        long category = category(null, "패리티 분류");
        long product = product(category, "패리티_상품%");
        long a = groupBuy(product, "복숭아 공구", "RECRUITING", Instant.parse("2001-01-01T00:00:00Z"));
        long b = groupBuy(product, "", "DRAFT", Instant.parse("2001-01-02T00:00:00Z"));
        groupBuy(product, "", "CANCELED", Instant.parse("2001-01-02T00:00:00Z"));
        groupBuy(product, "자두_공구", "RECRUITING", Instant.parse("2001-01-03T00:00:00Z"));
        GroupBuyAdminQueries store = new GroupBuyAdminQueries(query);

        for (String q : new String[] {null, "복숭아", "패리티_상품%", "_", "%", Long.toString(a), Long.toString(b), "없는말"}) {
            for (GroupBuyStatus status : new GroupBuyStatus[] {null, GroupBuyStatus.RECRUITING, GroupBuyStatus.DRAFT}) {
                StringBuilder where = new StringBuilder(" FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE 1 = 1");
                List<Object> args = new ArrayList<>();
                if (q != null) {
                    where.append(" AND (g.title ILIKE ? OR p.name ILIKE ? OR g.id::text = ?)");
                    String like = "%" + q.strip().replace("%", "\\%").replace("_", "\\_") + "%";
                    args.add(like);
                    args.add(like);
                    args.add(q.strip());
                }
                Map<String, Long> oldCounts = new TreeMap<>();
                jdbc.query("SELECT g.status, count(*)" + where + " GROUP BY g.status", rs -> {
                    oldCounts.put(rs.getString(1), rs.getLong(2));
                }, args.toArray());
                GroupBuyAdminQueries.ListFilter base = new GroupBuyAdminQueries.ListFilter(q == null ? null : GroupBuyRowNames.like(q.strip()), q == null ? null : q.strip(),
                        null);
                Map<String, Long> newCounts = new TreeMap<>();
                store.statusCounts(base).forEach((s, n) -> newCounts.put(s.name(), n));
                assertThat(newCounts).as("탭 수 q=%s", q).isEqualTo(oldCounts);
                if (status != null) {
                    where.append(" AND g.status = ?");
                    args.add(status.name());
                }
                GroupBuyAdminQueries.ListFilter filter = new GroupBuyAdminQueries.ListFilter(base.like(), base.exact(), status);
                assertThat(store.count(filter)).as("건수 q=%s status=%s", q, status).isEqualTo(jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray()));
                for (int page = 0; page < 3; page++) {
                    List<Object> pageArgs = new ArrayList<>(args);
                    pageArgs.add(2);
                    pageArgs.add(page * 2);
                    assertThat(store.pageIds(filter, page * 2L, 2)).as("쪽 q=%s status=%s page=%d", q, status, page)
                            .isEqualTo(jdbc.queryForList("SELECT g.id" + where + " ORDER BY g.updated_at DESC, g.id DESC LIMIT ? OFFSET ?", Long.class, pageArgs.toArray()));
                }
            }
        }
    }

    @Test
    void 공구_상세의_차수_선점_줄_요약_지연_안내_공급_불가가_예전_SQL_과_같다() {
        long category = category(null, "패리티 상세");
        long product = product(category, "상세 상품");
        long g = groupBuy(product, "", "RECRUITING", Instant.parse("2001-02-01T00:00:00Z"));
        long main = sku(g, "MAIN", "본품", 1);
        long addon = sku(g, "ADDON", "추가", 0);
        long hold = participation(g, 1L, "PENDING_PAYMENT", Instant.parse("2001-02-02T00:00:00Z"), null);
        item(hold, g, addon, 2, 0, 0, 0);
        item(hold, g, main, 1, 0, 0, 0);
        long empty = participation(g, 2L, "PENDING_PAYMENT", Instant.parse("2001-02-02T01:00:00Z"), null);
        jdbc.update("UPDATE group_buy_participations SET reserved_until = ? WHERE id IN (?, ?)", Timestamp.from(Instant.parse("2001-02-03T00:00:00Z")), hold, empty);
        jdbc.update("INSERT INTO group_buy_delay_notices (id, group_buy_id, previous_planned_at, new_planned_at, reason, operator_id, created_at) VALUES (?, ?, NULL, ?, '늦어요 시험', NULL, ?)",
                id(), g, java.sql.Date.valueOf(LocalDate.of(2001, 3, 1)), Timestamp.from(Instant.parse("2001-02-02T12:00:00Z")));
        GroupBuyAdminQueries store = new GroupBuyAdminQueries(query);

        Instant now = Instant.parse("2001-02-02T06:00:00Z");
        Map<Long, String> oldLines = new LinkedHashMap<>();
        Map<Long, Integer> oldQty = new LinkedHashMap<>();
        jdbc.query("SELECT pa.id, (SELECT string_agg(s.name || ' × ' || i.qty, ', ' ORDER BY s.kind DESC, s.sort_order) FROM group_buy_participation_items i "
                + "JOIN group_buy_skus s ON s.id = i.sku_id WHERE i.participation_id = pa.id), (SELECT sum(i.qty) FROM group_buy_participation_items i "
                + "WHERE i.participation_id = pa.id) FROM group_buy_participations pa WHERE pa.group_buy_id = ? AND pa.status = 'PENDING_PAYMENT' AND pa.reserved_until > ? "
                + "ORDER BY pa.reserved_until", rs -> {
            oldLines.put(rs.getLong(1), rs.getString(2) == null ? "" : rs.getString(2));
            oldQty.put(rs.getLong(1), rs.getInt(3));
        }, g, Timestamp.from(now));
        List<GroupBuyAdminQueries.HoldRow> holds = store.holds(g, now);
        assertThat(holds).extracting(GroupBuyAdminQueries.HoldRow::participationId).containsExactlyElementsOf(oldLines.keySet());
        Map<Long, GroupBuyAdminQueries.HoldLines> lines = store.holdLines(holds.stream().map(GroupBuyAdminQueries.HoldRow::participationId).toList());
        for (long pid : oldLines.keySet()) {
            GroupBuyAdminQueries.HoldLines l = lines.get(pid);
            assertThat(l == null ? "" : l.summary()).isEqualTo(oldLines.get(pid));
            assertThat(l == null ? 0 : l.qty()).isEqualTo(oldQty.get(pid));
        }
        assertThat(lines.get(hold).summary()).isEqualTo("본품 × 1, 추가 × 2");

        assertThat(store.rounds(g)).extracting(GroupBuyAdminQueries.RoundRow::title).containsExactly("상세 상품");
        assertThat(store.delayNotices(g)).singleElement().satisfies(n -> assertThat(n.notifiedOrders()).isEqualTo(jdbc.queryForObject(
                "SELECT count(*) FROM group_buy_participations p WHERE p.group_buy_id = ? AND p.joined_at <= ? AND p.status IN ('JOINED', 'CONFIRMED', 'PREPARING', 'CANCELED', "
                        + "'REFUNDED')", Integer.class, g, Timestamp.from(Instant.parse("2001-02-02T12:00:00Z")))));
        assertThat(store.categoryPath(category)).isEqualTo(jdbc.queryForObject("SELECT CASE WHEN pc.name IS NULL THEN c.name ELSE pc.name || ' › ' || c.name END "
                + "FROM group_buy_categories c LEFT JOIN group_buy_categories pc ON pc.id = c.parent_id WHERE c.id = ?", String.class, category));
        assertThat(store.returnedBySku(g).keySet()).containsExactlyInAnyOrder(main, addon);
    }

    // ── 주문 목록 ───────────────────────────────────────────────────────────

    @Test
    void 주문_목록의_찾기_날짜_알림_거르기_탭_수_쪽이_예전_SQL_과_같다() {
        long product = product(category(null, "주문 분류"), "주문 상품");
        long g = groupBuy(product, "주문 공구", "RECRUITING", Instant.parse("2001-03-01T00:00:00Z"));
        long user = member("주문닉_네임");
        List<Long> made = new ArrayList<>();
        made.add(participation(g, user, "CONFIRMED", Instant.parse("2001-03-02T00:00:00Z"), null));
        made.add(participation(g, member("주문닉_네임"), "SHIPPED", Instant.parse("2001-03-03T15:30:00Z"), Instant.parse("2001-03-04T00:00:00Z")));
        made.add(participation(g, 999_999L, "SHIPPED", Instant.parse("2001-03-05T00:00:00Z"), Instant.parse("2001-03-20T00:00:00Z")));
        made.add(participation(g, member("주문닉_네임"), "EXPIRED", Instant.parse("2001-03-06T00:00:00Z"), null));
        String orderNo = jdbc.queryForObject("SELECT order_no FROM group_buy_participations WHERE id = ?", String.class, made.get(0));
        GroupBuyOrderAdminQueries store = new GroupBuyOrderAdminQueries(query);

        record Case(Long groupBuyId, String q, LocalDate from, LocalDate to, String alert, List<Long> overdue, ParticipationStatus status) {
        }
        Instant now = Instant.parse("2001-03-25T00:00:00Z");
        List<Case> cases = List.of(new Case(null, null, null, null, null, null, null), new Case(g, null, null, null, null, null, null),
                new Case(g, orderNo, null, null, null, null, null), new Case(g, "주문닉_네", null, null, null, null, null), new Case(g, Long.toString(user), null, null, null, null, null),
                new Case(g, null, LocalDate.of(2001, 3, 3), LocalDate.of(2001, 3, 4), null, null, null), new Case(g, null, null, null, "SHIP_OVERDUE", List.of(), null),
                new Case(g, null, null, null, "SHIP_OVERDUE", List.of(made.get(0), made.get(2)), null), new Case(g, null, null, null, "DELIVERY_STALE", null, null),
                new Case(g, null, null, null, null, null, ParticipationStatus.SHIPPED), new Case(null, null, null, null, null, null, ParticipationStatus.CONFIRMED));
        for (Case c : cases) {
            StringBuilder where = new StringBuilder(" FROM group_buy_participations pa LEFT JOIN profiles pr ON pr.user_id = pa.user_id WHERE pa.status <> 'EXPIRED'");
            List<Object> args = new ArrayList<>();
            if (c.groupBuyId() != null) {
                where.append(" AND pa.group_buy_id = ?");
                args.add(c.groupBuyId());
            }
            if (c.q() != null) {
                where.append(" AND (pa.order_no = ? OR pr.nickname ILIKE ? OR pa.user_id::text = ?)");
                args.add(c.q());
                args.add("%" + c.q().replace("%", "\\%").replace("_", "\\_") + "%");
                args.add(c.q());
            }
            if (c.from() != null) {
                where.append(" AND pa.created_at >= ?");
                args.add(Timestamp.from(c.from().atStartOfDay(ServiceTime.ZONE).toInstant()));
            }
            if (c.to() != null) {
                where.append(" AND pa.created_at < ?");
                args.add(Timestamp.from(c.to().plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant()));
            }
            if ("SHIP_OVERDUE".equals(c.alert())) {
                where.append(" AND pa.id = ANY (?::bigint[])");
                args.add(c.overdue().toArray(new Long[0]));
            } else if ("DELIVERY_STALE".equals(c.alert())) {
                where.append(" AND pa.status = 'SHIPPED' AND pa.shipped_at < ?");
                args.add(Timestamp.from(now.minus(Duration.ofDays(7))));
            }
            Map<String, Long> oldCounts = new TreeMap<>();
            jdbc.query("SELECT pa.status, count(*)" + where + " GROUP BY pa.status", rs -> {
                oldCounts.put(rs.getString(1), rs.getLong(2));
            }, args.toArray());
            GroupBuyOrderAdminQueries.ListFilter filter = new GroupBuyOrderAdminQueries.ListFilter(c.groupBuyId(), c.q(), c.q() == null ? null : GroupBuyRowNames.like(c.q()),
                    c.from() == null ? null : c.from().atStartOfDay(ServiceTime.ZONE).toInstant(), c.to() == null ? null : c.to().plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant(),
                    c.overdue(), "DELIVERY_STALE".equals(c.alert()) ? now.minus(Duration.ofDays(7)) : null, null);
            Map<String, Long> newCounts = new TreeMap<>();
            store.statusCounts(filter).forEach((s, n) -> newCounts.put(s.name(), n));
            assertThat(newCounts).as("탭 수 %s", c).isEqualTo(oldCounts);
            if (c.status() != null) {
                where.append(" AND pa.status = ?");
                args.add(c.status().name());
                filter = filter.withStatus(c.status());
            }
            assertThat(store.count(filter)).as("건수 %s", c).isEqualTo(jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray()));
            for (int page = 0; page < 2; page++) {
                List<Object> pageArgs = new ArrayList<>(args);
                pageArgs.add(2);
                pageArgs.add(page * 2);
                assertThat(store.pageIds(filter, page * 2L, 2)).as("쪽 %s page=%d", c, page)
                        .isEqualTo(jdbc.queryForList("SELECT pa.id" + where + " ORDER BY pa.created_at DESC, pa.id DESC LIMIT ? OFFSET ?", Long.class, pageArgs.toArray()));
            }
        }
        // 회원 표시 — 회원 줄이 없어도 프로필 닉네임은 읽는다(예전 (SELECT ?::bigint) x LEFT JOIN users · profiles)
        assertThat(store.member(user)).isEqualTo(new GroupBuyOrderAdminQueries.MemberRow(jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, user), false));
        assertThat(store.member(999_999L).gone()).isTrue();
    }

    // ── 요청 대기열 ─────────────────────────────────────────────────────────

    @Test
    void 요청_대기열의_기한_경고만_거르기와_쪽이_예전_메모리_셈과_같다() {
        long product = product(category(null, "요청 분류"), "요청 상품");
        long g = groupBuy(product, "요청 공구", "RECRUITING", Instant.parse("2001-04-01T00:00:00Z"));
        long user = member("요청닉");
        Instant now = Instant.now();
        // 시작이 여러 날 전인 열린 요청·닫힌 요청 — 취소는 요청 시각, 반품은 돌려받은 시각(없으면 단계 없음)
        for (int days = 0; days < 9; days++) {
            long pa = participation(g, member("요청닉"), "CONFIRMED", now.minus(Duration.ofDays(days + 1)), null);
            Instant created = now.minus(Duration.ofDays(days)).minus(Duration.ofHours(days % 3));
            request(pa, g, user, "CANCEL", days % 4 == 3 ? "WITHDRAWN" : "REQUESTED", created, null);
            long pb = participation(g, member("요청닉"), "DELIVERED", now.minus(Duration.ofDays(days + 2)), null);
            request(pb, g, user, "RETURN", days % 3 == 0 ? "APPROVED" : "RECEIVED", created.minus(Duration.ofDays(2)), days % 3 == 0 ? null : created);
        }
        GroupBuyRequestAdminQueries store = new GroupBuyRequestAdminQueries(query);
        CommerceGroupBuySettingsQueries settings = new CommerceGroupBuySettingsQueries(query);
        BusinessDayCalendar calendar = BusinessDayCalendar.of(jdbc.queryForList("SELECT holiday_date FROM site_holidays", java.sql.Date.class).stream().map(java.sql.Date::toLocalDate)
                .toList());

        for (String q : new String[] {null, "요청닉"}) {
            for (List<RequestStatus> statuses : java.util.Arrays.asList(null, List.of(RequestStatus.APPROVED, RequestStatus.RECEIVED), List.of(RequestStatus.REQUESTED))) {
                StringBuilder where = new StringBuilder(" FROM group_buy_requests r JOIN group_buy_participations pa ON pa.id = r.participation_id "
                        + "LEFT JOIN profiles pr ON pr.user_id = r.user_id WHERE r.status <> 'AWAITING_FEE'");
                List<Object> args = new ArrayList<>();
                if (q != null) {
                    where.append(" AND (pa.order_no = ? OR pr.nickname ILIKE ? OR r.user_id::text = ?)");
                    args.add(q);
                    args.add("%" + q + "%");
                    args.add(q);
                }
                if (statuses != null) {
                    where.append(statuses.size() == 2 ? " AND r.status IN ('APPROVED', 'RECEIVED')" : " AND r.status = 'REQUESTED'");
                }
                // 예전: 모두 읽어 줄마다 단계를 셈 → 경고(OK 아님)만 → 메모리에서 쪽 나누기
                List<Long> oldWarn = new ArrayList<>();
                jdbc.query("SELECT r.id, r.type, r.status, r.created_at, r.received_at" + where + " ORDER BY r.created_at, r.id", rs -> {
                    Timestamp start = "CANCEL".equals(rs.getString(2)) ? rs.getTimestamp(4) : rs.getTimestamp(5);
                    boolean open = RequestStatus.OPEN.contains(RequestStatus.valueOf(rs.getString(3)));
                    if (start != null && open && GroupBuyBusinessDays.deadlineLevel(start.toInstant(), now, calendar) != GroupBuyBusinessDays.Level.NONE) {
                        oldWarn.add(rs.getLong(1));
                    }
                }, args.toArray());
                Instant warnBefore = GroupBuyBusinessDays.elapsedCutoff(ServiceTime.dateOf(now), 2, calendar).atStartOfDay(ServiceTime.ZONE).toInstant();
                GroupBuyRequestAdminQueries.ListFilter filter = new GroupBuyRequestAdminQueries.ListFilter(null, q, q == null ? null : GroupBuyRowNames.like(q), statuses,
                        warnBefore);
                assertThat(store.count(filter)).as("경고 건수 q=%s statuses=%s", q, statuses).isEqualTo(oldWarn.size());
                for (int page = 0; page * 3 <= oldWarn.size(); page++) {
                    assertThat(store.pageIds(filter, page * 3L, 3)).as("경고 쪽 q=%s statuses=%s page=%d", q, statuses, page)
                            .isEqualTo(oldWarn.stream().skip(page * 3L).limit(3).toList());
                }
                List<Long> oldAll = jdbc.queryForList("SELECT r.id" + where + " ORDER BY r.created_at, r.id", Long.class, args.toArray());
                GroupBuyRequestAdminQueries.ListFilter all = new GroupBuyRequestAdminQueries.ListFilter(null, q, q == null ? null : GroupBuyRowNames.like(q), statuses, null);
                assertThat(store.count(all)).isEqualTo(oldAll.size());
                assertThat(store.pageIds(all, 0, 1_000_000)).isEqualTo(oldAll);
            }
        }

        // 대시보드 열린 요청 — 예전: 열린 요청을 다 읽어 단계를 셈
        long total = 0;
        long warn = 0;
        long critical = 0;
        for (Map<String, Object> r : jdbc.queryForList("SELECT type, created_at, received_at FROM group_buy_requests WHERE status IN ('REQUESTED', 'APPROVED', 'RECEIVED')")) {
            total++;
            Timestamp start = "CANCEL".equals(r.get("type")) ? (Timestamp) r.get("created_at") : (Timestamp) r.get("received_at");
            if (start == null) {
                continue;
            }
            GroupBuyBusinessDays.Level level = GroupBuyBusinessDays.deadlineLevel(start.toInstant(), now, calendar);
            if (level == GroupBuyBusinessDays.Level.CRITICAL) {
                critical++;
            } else if (level == GroupBuyBusinessDays.Level.WARNING) {
                warn++;
            }
        }
        LocalDate today = ServiceTime.dateOf(now);
        long newCritical = settings.openRequestsStartedBefore(GroupBuyBusinessDays.elapsedCutoff(today, 3, calendar).atStartOfDay(ServiceTime.ZONE).toInstant());
        long newWarn = settings.openRequestsStartedBefore(GroupBuyBusinessDays.elapsedCutoff(today, 2, calendar).atStartOfDay(ServiceTime.ZONE).toInstant()) - newCritical;
        assertThat(settings.openRequests()).isEqualTo(total);
        assertThat(newCritical).isEqualTo(critical);
        assertThat(newWarn).isEqualTo(warn);
        assertThat(critical + warn).as("시험 자료가 기한 단계를 만든다(경고·심각 비율은 오늘 요일에 따라 다르다)").isPositive();
    }

    // ── 상품 목록 ───────────────────────────────────────────────────────────

    @Test
    void 상품_목록의_찾기_분류_건수_쪽_대표_이미지가_예전_SQL_과_같다() {
        long parent = category(null, "상위 분류");
        long child = category(parent, "하위 분류");
        long p1 = product(parent, "사과_박스");
        long p2 = product(child, "배 상자");
        product(child, "감 100%");
        jdbc.update("INSERT INTO group_buy_product_images (id, product_id, image_key, thumb_key, sort_order, created_at) VALUES (?, ?, 'a.jpg', NULL, 1, now()), "
                + "(?, ?, 'b.jpg', 'b-t.jpg', 0, now()), (?, ?, 'c.jpg', NULL, 0, now())", id(), p1, id(), p1, id(), p2);
        groupBuy(p1, "사과 공구", "RECRUITING", Instant.parse("2001-05-01T00:00:00Z"));
        GroupBuyProductAdminQueries store = new GroupBuyProductAdminQueries(query);

        for (String q : new String[] {null, "사과_", "100%", "상자"}) {
            for (Long category : new Long[] {null, parent, child}) {
                StringBuilder where = new StringBuilder(" FROM group_buy_products p WHERE 1 = 1");
                List<Object> args = new ArrayList<>();
                if (q != null) {
                    where.append(" AND p.name ILIKE ?");
                    args.add("%" + q.replace("%", "\\%").replace("_", "\\_") + "%");
                }
                if (category != null) {
                    where.append(" AND (p.category_id = ? OR p.category_id IN (SELECT id FROM group_buy_categories WHERE parent_id = ?))");
                    args.add(category);
                    args.add(category);
                }
                String like = q == null ? null : GroupBuyRowNames.like(q);
                assertThat(store.productCount(like, category)).isEqualTo(jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray()));
                List<Object> pageArgs = new ArrayList<>(args);
                pageArgs.add(2);
                pageArgs.add(0);
                record Old(long id, int skus, int groupBuys, int running, String thumb) {
                }
                List<Old> old = jdbc.query("SELECT p.id, (SELECT count(*) FROM group_buy_product_skus k WHERE k.product_id = p.id), (SELECT count(*) FROM group_buys g "
                        + "WHERE g.product_id = p.id), (SELECT count(*) FROM group_buys g WHERE g.product_id = p.id AND g.status IN ('RECRUITING', 'CLOSING')), "
                        + "(SELECT COALESCE(i.thumb_key, i.image_key) FROM group_buy_product_images i WHERE i.product_id = p.id ORDER BY i.sort_order LIMIT 1)" + where
                        + " ORDER BY p.updated_at DESC, p.id DESC LIMIT ? OFFSET ?", (rs, i) -> new Old(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5)),
                        pageArgs.toArray());
                List<GroupBuyProductAdminQueries.ProductListRow> rows = store.productPage(like, category, 0, 2);
                Map<Long, String> thumbs = store.thumbKeys(rows.stream().map(GroupBuyProductAdminQueries.ProductListRow::id).toList());
                assertThat(rows.stream().map(r -> new Old(r.id(), r.skuCount(), r.groupBuyCount(), r.runningCount(), thumbs.get(r.id()))).toList())
                        .as("쪽 q=%s category=%s", q, category).isEqualTo(old);
            }
        }
        assertThat(store.thumbKeys(List.of(p1))).containsEntry(p1, "b-t.jpg");
        assertThat(store.lockedByPublished(p1)).isTrue();
        assertThat(store.uses(p1)).extracting(GroupBuyProductAdminQueries.UseRow::title).containsExactly("사과 공구");
    }

    // ── 넣기 도우미 ─────────────────────────────────────────────────────────

    private static long id() {
        return ThreadLocalRandom.current().nextLong(1L << 60, Long.MAX_VALUE);
    }

    private long category(Long parent, String name) {
        long id = id();
        Timestamp t = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO group_buy_categories (id, parent_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, parent, name + (id % 100_000_000L), t, t);
        return id;
    }

    private long product(long category, String name) {
        long id = id();
        Timestamp t = Timestamp.from(Instant.now().minusSeconds(ThreadLocalRandom.current().nextInt(1, 10_000)));
        jdbc.update("INSERT INTO group_buy_products (id, category_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, category, name, t, t);
        jdbc.update("INSERT INTO group_buy_product_skus (id, product_id, name) VALUES (?, ?, '기본')", id(), id);
        return id;
    }

    /** 공구 한 줄 — 고친 시각 = {@code updated} */
    private long groupBuy(long product, String title, String status, Instant updated) {
        long id = id();
        boolean draft = "DRAFT".equals(status);
        Instant end = Instant.parse("2001-12-01T00:00:00Z");
        jdbc.update("INSERT INTO group_buys (id, product_id, title, group_price, shipping_policy_code, shipping_fee, return_shipping_fee, return_fee_mode, min_qty, max_qty, "
                        + "initial_max_qty, current_qty, start_at, end_at, fulfillment, ship_within_business_days, status, published_at, round_root_id, revision_no, created_at, "
                        + "updated_at) VALUES (?, ?, ?, 10000, 'DEFAULT', ?, ?, ?, 0, 10, ?, 0, ?, ?, 'IMMEDIATE', 3, ?, ?, ?, ?, ?, ?)",
                id, product, title, draft ? null : 3000L, draft ? null : 3000L, draft ? null : "ONE_WAY", draft ? null : 10, Timestamp.from(end.minus(Duration.ofDays(30))),
                Timestamp.from(end), status, draft ? null : Timestamp.from(updated), id, draft ? 0 : 1, Timestamp.from(updated), Timestamp.from(updated));
        return id;
    }

    private long sku(long groupBuy, String kind, String name, int sortOrder) {
        long productSku = id();
        jdbc.update("INSERT INTO group_buy_product_skus (id, product_id, name) SELECT ?, product_id, ? FROM group_buys WHERE id = ?", productSku, name + productSku % 1000, groupBuy);
        long id = id();
        jdbc.update("INSERT INTO group_buy_skus (id, group_buy_id, kind, product_sku_id, name, extra_price, addon_price, sort_order) VALUES (?, ?, ?, ?, ?, 0, ?, ?)", id,
                groupBuy, kind, productSku, name, "ADDON".equals(kind) ? 1000L : null, sortOrder);
        return id;
    }

    private long member(String nickname) {
        long id = id();
        jdbc.update("INSERT INTO users (id, email, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, now(), 'v1', now(), now())", id,
                "parity-" + id + "@test.local");
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, nickname + (id % 1_000_000));
        return id;
    }

    private long participation(long groupBuy, long userId, String status, Instant created, Instant shippedAt) {
        long id = id();
        jdbc.update("INSERT INTO group_buy_participations (id, order_no, group_buy_id, user_id, status, items_amount, shipping_fee, amount, cash_amount, group_buy_revision, "
                        + "reserved_until, joined_at, shipped_at, expire_cause, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 10000, 0, 10000, 10000, 1, ?, ?, ?, ?, ?, ?)", id,
                "G" + String.format("%019d", id % 10_000_000_000_000_000L), groupBuy, userId, status, Timestamp.from(created.plusSeconds(600)), Timestamp.from(created),
                shippedAt == null ? null : Timestamp.from(shippedAt), "EXPIRED".equals(status) ? "HOLD_TIMEOUT" : null, Timestamp.from(created), Timestamp.from(created));
        return id;
    }

    private void item(long participation, long groupBuy, long sku, int qty, int canceled, int returned, int restocked) {
        jdbc.update("INSERT INTO group_buy_participation_items (id, participation_id, group_buy_id, sku_id, qty, unit_price, item_amount, canceled_qty, returned_qty, restocked_qty) "
                + "VALUES (?, ?, ?, ?, ?, 1000, ?, ?, ?, ?)", id(), participation, groupBuy, sku, qty, qty * 1000L, canceled, returned, restocked);
    }

    private void request(long participation, long groupBuy, long userId, String type, String status, Instant created, Instant receivedAt) {
        boolean returning = "RETURN".equals(type);
        jdbc.update("INSERT INTO group_buy_requests (id, participation_id, group_buy_id, user_id, type, status, preview_amount, collection_method, fee_amount, received_at, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1000, ?, 0, ?, ?, ?)", id(), participation, groupBuy, userId, type, status,
                returning ? "COMPANY_PICKUP" : null, receivedAt == null ? null : Timestamp.from(receivedAt), Timestamp.from(created), Timestamp.from(created));
    }
}
