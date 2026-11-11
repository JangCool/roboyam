package com.community.dashboard;

import static com.community.dashboard.SalesPeriodsTest.kst;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.repository.master.VisitAnalyticsStore;
import com.community.analytics.service.VisitUniqueCounts;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.response.gadget.DashboardReferrersView;
import com.community.dashboard.dto.response.gadget.DashboardVisitorsView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import com.community.dashboard.service.gadget.ReferrersGadget;
import com.community.dashboard.service.gadget.VisitorsGadget;
import com.community.permission.dto.AdminActor;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 대시보드 방문 가젯(site-analytics-contract · admin-dashboard-contract) — 집계 표만 읽는다. 실제 PostgreSQL, 시험마다 연결 하나의 트랜잭션으로 넣고 되돌린다.
 * 분석 표는 api 모듈의 analytics 스트림 파일로 만든다. 시각은 다른 시험과 겹치지 않는 먼 미래(2096년).
 */
class DashboardAnalyticsGadgetsTest {

    private static DataSource base;
    private Connection connection;
    private JdbcTemplate jdbc;
    private DashboardAnalyticsStore store;

    @BeforeAll
    static void tables() {
        base = CommerceTestDatabase.migrated("admin");
        new SchemaMigration("filesystem:../api/src/main/resources/db/migration/").migrateStream(base, SchemaMigration.ANALYTICS);
    }

    @BeforeEach
    void open() throws Exception {
        connection = base.getConnection();
        connection.setAutoCommit(false);
        SingleConnectionDataSource single = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(single);
        store = new DashboardAnalyticsStore(new AnalyticsDatabase(single, false), single, null); // 이름 붙이기(JPA)는 이 시험이 쓰지 않는다
    }

    @AfterEach
    void close() throws Exception {
        connection.rollback();
        connection.close();
    }

    private void hour(Instant hour, long visitors, long newVisitors, long views) {
        jdbc.update("INSERT INTO analytics_visit_hourly (hour, visitors, new_visitors, page_views) VALUES (?, ?, ?, ?)", Timestamp.from(hour), visitors, newVisitors, views);
    }

    private static DashboardGadgetQuery query(Instant now) {
        return new DashboardGadgetQuery(new AdminActor(1L, "시험", "t@test.local", false, Set.of("site.read"), "127.0.0.1"), 5, 7, now);
    }

    @Test
    void 방문_현황은_한국_0시부터_오늘을_세고_어제는_같은_시각이_든_시간_칸까지만_센다() {
        Instant now = kst(2096, 3, 15, 10, 20);
        hour(kst(2096, 3, 14, 0, 0), 5, 1, 50); // 어제 0시
        hour(kst(2096, 3, 14, 10, 0), 7, 2, 70); // 어제 같은 시각이 든 칸(10시) — 넣는다
        hour(kst(2096, 3, 14, 11, 0), 100, 9, 1000); // 어제 11시 — 같은 시각 뒤
        hour(kst(2096, 3, 14, 23, 0), 100, 9, 1000);
        hour(kst(2096, 3, 13, 23, 0), 100, 9, 1000); // 그제
        hour(kst(2096, 3, 15, 0, 0), 3, 1, 30); // 오늘 0시(UTC 로는 전날 15시)
        hour(kst(2096, 3, 15, 9, 0), 4, 2, 40);
        hour(kst(2096, 3, 15, 10, 0), 2, 0, 20);
        hour(kst(2096, 3, 15, 11, 0), 100, 9, 1000); // 미래 칸(있을 수 없지만 넣어도 안 센다)
        jdbc.update("INSERT INTO analytics_visit_active (visitor_hash, last_seen) VALUES ('qa-active-1', ?), ('qa-active-2', ?) ON CONFLICT (visitor_hash) DO UPDATE "
                + "SET last_seen = EXCLUDED.last_seen", Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.minusSeconds(301)));

        DashboardVisitorsView v = (DashboardVisitorsView) new VisitorsGadget(store).load(query(now));

        assertThat(v.visitorsToday()).isEqualTo(9);
        assertThat(v.newVisitorsToday()).isEqualTo(3);
        assertThat(v.pageViewsToday()).isEqualTo(90);
        assertThat(v.visitorsYesterdaySameTime()).isEqualTo(12);
        assertThat(v.pageViewsYesterdaySameTime()).isEqualTo(120);
        assertThat(v.activeNow()).as("지금 접속 = 5분 안").isEqualTo(1);
        assertThat(v.hours()).hasSize(11);
        assertThat(v.hours().get(0).visitors()).isEqualTo(3);
        assertThat(v.hours().get(9).visitors()).isEqualTo(4);
        assertThat(v.hours().get(10).visitors()).isEqualTo(2);
        assertThat(v.hours().get(5).visitors()).isZero();
    }

    @Test
    void 자정_직후에는_오늘_0시_칸과_어제_0시_칸만_비교한다() {
        Instant now = kst(2096, 4, 1, 0, 0);
        hour(kst(2096, 3, 31, 0, 0), 6, 0, 60);
        hour(kst(2096, 3, 31, 1, 0), 100, 0, 1000);
        hour(kst(2096, 4, 1, 0, 0), 1, 1, 10);
        DashboardVisitorsView v = (DashboardVisitorsView) new VisitorsGadget(store).load(query(now));
        assertThat(v.visitorsToday()).isEqualTo(1);
        assertThat(v.visitorsYesterdaySameTime()).isEqualTo(6);
        assertThat(v.hours()).hasSize(1);
    }

    @Test
    void 방문_현황의_이번_주_이번_달_순방문자는_순방문자_카드의_방문자_이번_기간과_같다() {
        // 3단계 결정 ④ — 실제 생성자(순방문자 숫자 표를 읽는다). 세기 시작일은 숫자 표 전체의 가장 이른 날이라 트랜잭션 안에서 비우고 시작한다(되돌림)
        jdbc.update("DELETE FROM analytics_unique_counts");
        Instant now = kst(2096, 6, 13, 15, 0); // 수요일
        assertThat(LocalDate.of(2096, 6, 13).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.WEDNESDAY);
        VisitAnalyticsStore visits = new VisitAnalyticsStore(new AnalyticsDatabase(new SingleConnectionDataSource(connection, true), false));
        VisitorsGadget gadget = new VisitorsGadget(store, visits);

        DashboardVisitorsView none = (DashboardVisitorsView) gadget.load(query(now));
        assertThat(none.visitorsThisWeek()).as("숫자 표가 비었다 — 집계 전").isNull();
        assertThat(none.visitorsThisMonth()).isNull();

        String sql = "INSERT INTO analytics_unique_counts (audience, period_kind, period_start, day, added) VALUES (?, ?, ?, ?, ?)";
        jdbc.update(sql, "VISITOR", "MONTH", Date.valueOf("2096-05-01"), Date.valueOf("2096-05-20"), 40); // 지난달 — 세기 시작
        jdbc.update(sql, "VISITOR", "WEEK", Date.valueOf("2096-06-04"), Date.valueOf("2096-06-05"), 30); // 지난 주
        jdbc.update(sql, "VISITOR", "WEEK", Date.valueOf("2096-06-11"), Date.valueOf("2096-06-11"), 5); // 이번 주
        jdbc.update(sql, "VISITOR", "WEEK", Date.valueOf("2096-06-11"), Date.valueOf("2096-06-13"), 2);
        jdbc.update(sql, "VISITOR", "MONTH", Date.valueOf("2096-06-01"), Date.valueOf("2096-06-01"), 20); // 이번 달
        jdbc.update(sql, "VISITOR", "MONTH", Date.valueOf("2096-06-01"), Date.valueOf("2096-06-13"), 3);
        jdbc.update(sql, "MEMBER", "WEEK", Date.valueOf("2096-06-11"), Date.valueOf("2096-06-12"), 99); // 회원은 이 한 줄에 쓰지 않는다
        jdbc.update(sql, "MEMBER", "MONTH", Date.valueOf("2096-06-01"), Date.valueOf("2096-06-12"), 99);

        DashboardVisitorsView v = (DashboardVisitorsView) gadget.load(query(now));
        VisitAnalyticsAdmin.UniquesAudience card = new VisitUniqueCounts(visits).audiences(LocalDate.of(2096, 6, 13), true).get("VISITOR");

        assertThat(v.visitorsThisWeek()).isEqualTo(7L).isEqualTo(card.week().current().count());
        assertThat(v.visitorsThisMonth()).isEqualTo(23L).isEqualTo(card.month().current().count());

        // 세기 시작 뒤 빈 주는 0(null 아님) — 다음 주 월요일 자정 직후
        DashboardVisitorsView next = (DashboardVisitorsView) gadget.load(query(kst(2096, 6, 18, 0, 5)));
        assertThat(next.visitorsThisWeek()).isZero();
        assertThat(next.visitorsThisMonth()).isEqualTo(23L);
    }

    @Test
    void 유입_경로는_오늘_분류별_합과_도메인_상위이고_도메인이_없는_분류는_도메인_목록에_없다() {
        LocalDate day = LocalDate.of(2096, 5, 2);
        String sql = "INSERT INTO analytics_referrer_daily (day, referrer_group, referrer_domain, visits) VALUES (?, ?, ?, ?)";
        jdbc.update(sql, Date.valueOf(day), "SEARCH", "www.google.com", 5);
        jdbc.update(sql, Date.valueOf(day), "SEARCH", "search.naver.com", 7);
        jdbc.update(sql, Date.valueOf(day), "DIRECT", "", 20);
        jdbc.update(sql, Date.valueOf(day), "CAMPAIGN", "", 2);
        jdbc.update(sql, Date.valueOf(day), "SOCIAL", "t.co", 1);
        jdbc.update(sql, Date.valueOf(day.minusDays(1)), "OTHER_SITE", "old.example", 99);

        DashboardReferrersView v = (DashboardReferrersView) new ReferrersGadget(store).load(query(kst(2096, 5, 2, 12, 0)));

        assertThat(v.total()).isEqualTo(35);
        assertThat(v.groups()).extracting(DashboardReferrersView.Group::group).containsExactly("DIRECT", "SEARCH", "CAMPAIGN", "SOCIAL", "OTHER_SITE");
        assertThat(v.groups().get(1).visits()).isEqualTo(12);
        assertThat(v.domains()).extracting(DashboardReferrersView.Domain::domain).containsExactly("search.naver.com", "www.google.com", "t.co");
    }
}
