package com.community.dashboard;

import static com.community.dashboard.SalesPeriodsTest.kst;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.response.gadget.DashboardGroupBuyProgressView;
import com.community.dashboard.dto.response.gadget.DashboardGroupBuyQnaView;
import com.community.dashboard.dto.response.gadget.DashboardOrderTrendView;
import com.community.dashboard.dto.response.gadget.DashboardReconcileView;
import com.community.dashboard.dto.response.gadget.DashboardSalesSummaryView;
import com.community.dashboard.dto.response.gadget.DashboardTodayScheduleView;
import com.community.dashboard.repository.master.DashboardCommerceStore;
import com.community.dashboard.service.gadget.GroupBuyProgressGadget;
import com.community.dashboard.service.gadget.GroupBuyQnaGadget;
import com.community.dashboard.service.gadget.OrderTrendGadget;
import com.community.dashboard.service.gadget.ReconcileGadget;
import com.community.dashboard.service.gadget.SalesSummaryGadget;
import com.community.dashboard.service.gadget.TodayScheduleGadget;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.message.Messages;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import com.community.testsupport.JpaTestWiring;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 관리자 대시보드 공구·결제 가젯(admin-dashboard-contract 2단계 · commerce.md 16-2 매출 기준) — 실제 PostgreSQL({@link CommerceTestDatabase}, commerce.md 18절).
 *
 * <p>시험마다 연결 하나를 트랜잭션으로 열어 행을 넣고 끝나면 되돌린다(다른 시험 클래스가 같은 DB 를 쓴다 — 표를 비우지 않는다). 시각 범위로 세는 값은 다른 시험의 행과 겹치지 않는
 * 먼 시각(2091·2099년)을, 전체를 세는 값(답변 대기·열린 이슈)은 넣기 전과의 차이를, 목록은 맨 앞에 오도록 이른 시각(2000년)을 쓴다.
 */
class DashboardCommerceGadgetsTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** 시험마다 바뀌는 연결 — JPA 문맥(QueryDSL 읽기)도 같은 연결·같은 되돌릴 트랜잭션을 쓰게 한다(닫기는 막는다) */
    private static final CurrentConnection CURRENT = new CurrentConnection();
    private static AnnotationConfigApplicationContext jpa;

    private Connection connection;
    private JdbcTemplate jdbc;
    private DashboardCommerceStore store;

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
        DataSource base = CommerceTestDatabase.migrated("admin");
        connection = base.getConnection();
        connection.setAutoCommit(false);
        SingleConnectionDataSource single = new SingleConnectionDataSource(connection, true);
        CURRENT.single = single;
        if (jpa == null) {
            jpa = new AnnotationConfigApplicationContext(JpaWiring.class);
        }
        jdbc = new JdbcTemplate(single);
        store = new DashboardCommerceStore(jpa.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class), single);
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

    // ── 매출 요약 ───────────────────────────────────────────────────────────

    @Test
    void 매출_요약은_결제일_기준_결제액과_환불_완료일_기준_끝난_PG_환불을_한국_날짜로_센다() {
        Instant now = kst(2091, 3, 15, 10, 0);
        // 오늘: 0시 정각 · 0시 30분(UTC 로는 전날) · 9시 59분, 결제 종류는 공구·충전 모두
        order("GROUP_BUY", "PAID", null, 10_000, kst(2091, 3, 15, 0, 0));
        order("GROUP_BUY", "PARTIAL_CANCELED", null, 4_000, kst(2091, 3, 15, 0, 30));
        order("POINT_CHARGE", "PAID", null, 5_000, kst(2091, 3, 15, 9, 59));
        // 어제 결제했다가 전부 취소된 결제도 결제일에 센다
        order("BIZ_APPLICATION", "CANCELED", null, 3_000, kst(2091, 3, 14, 23, 59));
        // 이번 달 1일 0시
        long march1 = order("GROUP_BUY", "PAID", null, 20_000, kst(2091, 3, 1, 0, 0));
        // 지난달 같은 기간: 2월 15일 10시 앞까지만
        long feb = order("GROUP_BUY", "PAID", null, 7_000, kst(2091, 2, 15, 9, 59));
        order("GROUP_BUY", "PAID", null, 8_000, kst(2091, 2, 15, 10, 0));
        // 매출이 아닌 결제: 결제 시험 · 이상 결제(늦은 결제 — 자동 취소 대상) · 결제 전 · 만료
        order("PAYMENT_TEST", "PAID", null, 90_000, kst(2091, 3, 15, 8, 0));
        long late = order("GROUP_BUY", "CANCELED", "LATE_PAYMENT", 90_000, kst(2091, 3, 15, 8, 0));
        order("GROUP_BUY", "READY", null, 90_000, null);
        order("GROUP_BUY", "EXPIRED", null, 90_000, null);

        // 환불: 결제일이 아니라 환불 완료일로 센다
        cancel(march1, 1_000, "SUCCEEDED", null, kst(2091, 3, 15, 8, 0));
        cancel(march1, 500, "MANUAL", "CONSOLE_CANCEL", kst(2091, 3, 14, 12, 0));
        cancel(feb, 600, "SUCCEEDED", null, kst(2091, 2, 10, 12, 0));
        // 세지 않는 취소: 계좌 송금(PG 환불 아님) · 재시도 포기 · 아직 처리 중 · 이상 결제의 자동 취소
        cancel(march1, 700, "MANUAL", "BANK_TRANSFER", kst(2091, 3, 15, 8, 0));
        cancel(march1, 900, "GAVE_UP", null, kst(2091, 3, 15, 8, 0));
        cancel(march1, 300, "PENDING", null, null);
        cancel(late, 2_000, "SUCCEEDED", null, kst(2091, 3, 15, 8, 30));

        DashboardSalesSummaryView v = (DashboardSalesSummaryView) new SalesSummaryGadget(store).load(query(now, Permission.COMMERCE_PAYMENT_READ));

        assertThat(v.today()).isEqualTo(new DashboardSalesSummaryView.Period(19_000, 3, 1_000, 1));
        assertThat(v.yesterday()).isEqualTo(new DashboardSalesSummaryView.Period(3_000, 1, 500, 1));
        assertThat(v.month()).isEqualTo(new DashboardSalesSummaryView.Period(42_000, 5, 1_500, 2));
        assertThat(v.lastMonth()).isEqualTo(new DashboardSalesSummaryView.Period(7_000, 1, 600, 1));
    }

    @Test
    void 계좌_송금_환불은_송금_완료가_기록된_것만_송금_완료_시각에_환불로_센다() {
        Instant now = kst(2092, 5, 20, 10, 0);
        long groupBuy = groupBuy(product("송금 상품"), "송금 공구", "RECRUITING", 0, 10, 0, kst(2092, 6, 1, 0, 0), null);
        long paid = order("GROUP_BUY", "PAID", null, 50_000, kst(2092, 5, 1, 9, 0));
        // 오늘 송금 완료 — 전환(취소 줄 완료 시각)은 지난달 같은 기간 안이지만 송금 완료 시각으로 센다
        bankRefund(groupBuy, paid, 3_000, kst(2092, 4, 10, 12, 0), "SENT", kst(2092, 5, 20, 9, 0));
        // 어제 끝(23:59) 송금 완료
        bankRefund(groupBuy, paid, 4_000, kst(2092, 5, 19, 12, 0), "SENT", kst(2092, 5, 19, 23, 59));
        // 송금 전(계좌 있음 · 계좌 받기 전)은 처리 중이라 넣지 않는다
        bankRefund(groupBuy, paid, 2_000, kst(2092, 5, 20, 8, 0), "READY", null);
        bankRefund(groupBuy, paid, 1_500, kst(2092, 5, 20, 8, 0), "AWAITING_ACCOUNT", null);
        // 송금 대기 줄이 없는 계좌 송금 전환(완료 기록 없음)도 넣지 않는다
        cancel(paid, 700, "MANUAL", "BANK_TRANSFER", kst(2092, 5, 20, 8, 0));
        // 보상액 중 결제액을 넘는 몫(결제를 돌려준 것이 아니다)은 송금했어도 넣지 않는다
        transfer("GROUP_BUY_COMPENSATION", bankRefund(groupBuy, paid, 600, kst(2092, 5, 20, 8, 0), "AWAITING_ACCOUNT", null), 9_000, "SENT", kst(2092, 5, 20, 9, 30));
        // 결제 시험의 계좌 송금 환불은 매출 범위 밖
        long test = order("PAYMENT_TEST", "PAID", null, 9_000, kst(2092, 5, 20, 7, 0));
        bankRefund(groupBuy, test, 9_000, kst(2092, 5, 20, 8, 0), "SENT", kst(2092, 5, 20, 9, 0));
        // PG 환불과 함께 센다
        cancel(paid, 1_000, "SUCCEEDED", null, kst(2092, 5, 20, 8, 30));

        DashboardSalesSummaryView v = (DashboardSalesSummaryView) new SalesSummaryGadget(store).load(query(now, Permission.COMMERCE_PAYMENT_READ));

        assertThat(v.today()).isEqualTo(new DashboardSalesSummaryView.Period(0, 0, 4_000, 2));
        assertThat(v.yesterday()).isEqualTo(new DashboardSalesSummaryView.Period(0, 0, 4_000, 1));
        assertThat(v.month()).isEqualTo(new DashboardSalesSummaryView.Period(50_000, 1, 8_000, 3));
        assertThat(v.lastMonth()).isEqualTo(new DashboardSalesSummaryView.Period(0, 0, 0, 0));
    }

    @Test
    void 결제액은_청구액이_아니라_PG_가_알려_준_실제_결제액이다() {
        Instant now = kst(2091, 4, 20, 12, 0);
        long id = order("GROUP_BUY", "PAID", null, 10_000, kst(2091, 4, 20, 9, 0));
        jdbc.update("UPDATE payment_orders SET amount = 12000 WHERE id = ?", id); // 청구액과 결제액이 다른 줄(받아들인 결제)
        DashboardSalesSummaryView v = (DashboardSalesSummaryView) new SalesSummaryGadget(store).load(query(now, Permission.COMMERCE_PAYMENT_READ));
        assertThat(v.today().paid()).isEqualTo(10_000);
    }

    // ── 결제 추이 ───────────────────────────────────────────────────────────

    @Test
    void 결제_추이는_오늘_포함_N일을_한국_날짜로_묶고_빈_날은_0() {
        Instant now = kst(2091, 6, 15, 10, 0);
        order("GROUP_BUY", "PAID", null, 4_000, kst(2091, 6, 15, 0, 30)); // UTC 로는 14일
        order("GROUP_BUY", "PAID", null, 6_000, kst(2091, 6, 15, 9, 0));
        order("GROUP_BUY", "PAID", null, 2_000, kst(2091, 6, 9, 0, 0)); // 7일의 첫날 0시
        order("GROUP_BUY", "PAID", null, 9_000, kst(2091, 6, 8, 23, 59)); // 7일 밖
        order("GROUP_BUY", "CANCELED", "AMOUNT_MISMATCH", 9_000, kst(2091, 6, 12, 9, 0)); // 이상 결제
        order("PAYMENT_TEST", "PAID", null, 9_000, kst(2091, 6, 12, 9, 0));

        DashboardOrderTrendView v = (DashboardOrderTrendView) new OrderTrendGadget(store).load(query(now, 7, Permission.COMMERCE_PAYMENT_READ));

        assertThat(v.days()).hasSize(7);
        assertThat(v.days().get(0)).isEqualTo(new DashboardOrderTrendView.Day(LocalDate.of(2091, 6, 9), 2_000, 1));
        assertThat(v.days().get(3)).isEqualTo(new DashboardOrderTrendView.Day(LocalDate.of(2091, 6, 12), 0, 0));
        assertThat(v.days().get(6)).isEqualTo(new DashboardOrderTrendView.Day(LocalDate.of(2091, 6, 15), 10_000, 2));
        assertThat(v.days().get(5)).isEqualTo(new DashboardOrderTrendView.Day(LocalDate.of(2091, 6, 14), 0, 0));

        DashboardOrderTrendView month = (DashboardOrderTrendView) new OrderTrendGadget(store).load(query(now, 30, Permission.COMMERCE_PAYMENT_READ));
        assertThat(month.days()).hasSize(30);
        assertThat(month.days().get(0).date()).isEqualTo(LocalDate.of(2091, 5, 17));
        assertThat(month.days().stream().mapToLong(DashboardOrderTrendView.Day::amount).sum()).isEqualTo(21_000);
    }

    // ── 대사 ────────────────────────────────────────────────────────────────

    @Test
    void 대사는_최근_대사일과_열린_이슈_수_24시간_웹훅_처리_실패를_센다() {
        Instant now = Instant.parse("2099-12-31T12:00:00Z");
        long openBefore = store.openReconcileIssues();
        jdbc.update("INSERT INTO payment_reconcile_days (day, status, checked_count, issue_count, finished_at) VALUES (?, 'DONE', 3, 0, ?)", Date.valueOf("2099-12-30"),
                Timestamp.from(now));
        jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, detected_day, status, created_at) VALUES (?, 'AMOUNT_MISMATCH', ?, ?, 'OPEN', ?)", id(), paymentId(),
                Date.valueOf("2099-12-30"), Timestamp.from(now));
        jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, detected_day, status, resolve_note, resolved_by, resolved_at, created_at) "
                + "VALUES (?, 'PG_ONLY', ?, ?, 'RESOLVED', '처리함', 1, ?, ?)", id(), paymentId(), Date.valueOf("2099-12-30"), Timestamp.from(now), Timestamp.from(now));
        webhook("REJECTED", now.minus(Duration.ofHours(1)));
        webhook("RETRY", now.minus(Duration.ofHours(2)));
        webhook("RETRY", now.minus(Duration.ofHours(24)));
        webhook("REJECTED", now.minus(Duration.ofHours(24)).minusSeconds(1)); // 24시간 밖
        webhook("PROCESSED", now.minus(Duration.ofHours(1)));
        webhook("IGNORED", now.minus(Duration.ofHours(1)));

        DashboardReconcileView v = (DashboardReconcileView) new ReconcileGadget(store).load(query(now, Permission.COMMERCE_PAYMENT_READ));

        assertThat(v.lastDate()).isEqualTo(LocalDate.of(2099, 12, 30));
        assertThat(v.mismatched()).isEqualTo(openBefore + 1);
        assertThat(v.status()).isEqualTo("MISMATCHED");
        assertThat(v.webhookFailures()).isEqualTo(3);
    }

    @Test
    void 대사_상태는_열린_이슈가_먼저고_없으면_최근_날의_결과() {
        assertThat(ReconcileGadget.status("DONE", 2)).isEqualTo("MISMATCHED"); // 최근 날이 일치여도 처리 안 한 이슈가 있으면
        assertThat(ReconcileGadget.status("DONE", 0)).isEqualTo("MATCHED");
        assertThat(ReconcileGadget.status("MISMATCH", 0)).isEqualTo("MATCHED"); // 불일치를 모두 처리했다
        assertThat(ReconcileGadget.status("RUNNING", 0)).isEqualTo("PENDING");
        assertThat(ReconcileGadget.status("FAILED", 0)).isEqualTo("PENDING");
        assertThat(ReconcileGadget.status(null, 0)).isEqualTo("PENDING"); // 대사한 날이 없다
    }

    // ── 공구 ────────────────────────────────────────────────────────────────

    @Test
    void 모집_중_공구는_지금_마감_시각_차례이고_목표는_성사형_최소_수량_선착순_최대_수량() {
        long product = product("시험 상품");
        long a = groupBuy(product, "성사형 공구", "RECRUITING", 10, 40, 3, kst(2000, 1, 5, 12, 0), null);
        long b = groupBuy(product, "선착순 공구", "RECRUITING", 0, 50, 20, kst(2000, 1, 2, 12, 0), kst(2000, 1, 6, 9, 0)); // 연장 중 — 연장 끝이 마감
        long c = groupBuy(product, "", "RECRUITING", 0, 30, 0, kst(2000, 1, 5, 18, 0), null); // 제목이 비면 상품 이름
        groupBuy(product, "집계 중", "CLOSING", 0, 10, 1, kst(2000, 1, 1, 0, 0), null);
        groupBuy(product, "예정", "DRAFT", 0, 10, 0, kst(2000, 1, 1, 0, 0), null);

        DashboardGroupBuyProgressView v = (DashboardGroupBuyProgressView) new GroupBuyProgressGadget(store).load(query(Instant.now(), Permission.COMMERCE_PRODUCT_READ));

        assertThat(v.items()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(v.items().subList(0, 3)).containsExactly(
                new DashboardGroupBuyProgressView.Item(Long.toString(a), "성사형 공구", 3, 10, kst(2000, 1, 5, 12, 0)),
                new DashboardGroupBuyProgressView.Item(Long.toString(c), "시험 상품", 0, 30, kst(2000, 1, 5, 18, 0)),
                new DashboardGroupBuyProgressView.Item(Long.toString(b), "선착순 공구", 20, 50, kst(2000, 1, 6, 9, 0)));
        assertThat(v.items()).allMatch(i -> !i.title().equals("집계 중") && !i.title().equals("예정"));

        DashboardGroupBuyProgressView limited = (DashboardGroupBuyProgressView) new GroupBuyProgressGadget(store).load(
                new DashboardGadgetQuery(actor(Permission.COMMERCE_PRODUCT_READ), 5, 7, Instant.now()));
        assertThat(limited.items()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void 답변_대기_QnA_는_숨김_지움_답변된_질문을_빼고_비밀글은_본문을_싣지_않는다() {
        long product = product("Q&A 상품");
        long g = groupBuy(product, "Q&A 공구", "RECRUITING", 0, 10, 0, kst(2000, 2, 1, 0, 0), null);
        long before = store.openQuestionCount();
        long publicQ = question(g, "배송은\n\n  언제   오나요?", false, "OPEN", false, false, kst(2000, 1, 1, 0, 0));
        long secretQ = question(g, "제 번호는 010-1234-5678 입니다", true, "OPEN", false, false, kst(2000, 1, 1, 0, 1));
        long longQ = question(g, "가".repeat(150), false, "OPEN", false, false, kst(2000, 1, 1, 0, 2));
        question(g, "숨긴 질문", false, "OPEN", true, false, kst(2000, 1, 1, 0, 0));
        question(g, "지운 질문", false, "OPEN", false, true, kst(2000, 1, 1, 0, 0));
        question(g, "답변한 질문", false, "ANSWERED", false, false, kst(2000, 1, 1, 0, 0));

        DashboardGroupBuyQnaView v = (DashboardGroupBuyQnaView) new GroupBuyQnaGadget(store, messages()).load(
                new DashboardGadgetQuery(actor(Permission.COMMERCE_PRODUCT_READ), 10, 7, Instant.now()));

        assertThat(v.pending()).isEqualTo(before + 3);
        assertThat(v.oldest().subList(0, 3)).extracting(DashboardGroupBuyQnaView.Oldest::id)
                .containsExactly(Long.toString(publicQ), Long.toString(secretQ), Long.toString(longQ));
        assertThat(v.oldest().get(0).question()).isEqualTo("배송은 언제 오나요?");
        assertThat(v.oldest().get(0).groupBuyTitle()).isEqualTo("Q&A 공구");
        assertThat(v.oldest().get(1).question()).isEqualTo("비밀 질문이에요. Q&A 화면에서 확인해 주세요.").doesNotContain("010");
        assertThat(v.oldest().get(2).question()).isEqualTo("가".repeat(100) + "…");
        assertThat(v.oldest()).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void 오늘_일정의_공구_마감은_상품_조회_권한이_있을_때만_지금부터_내일_끝까지() {
        long product = product("일정 상품");
        long in = groupBuy(product, "내일 마감", "RECRUITING", 0, 10, 0, kst(2000, 1, 5, 12, 0), null);
        long extendedIn = groupBuy(product, "연장 끝 오늘", "RECRUITING", 0, 10, 0, kst(2000, 1, 3, 12, 0), kst(2000, 1, 4, 18, 0));
        groupBuy(product, "모레 마감", "RECRUITING", 0, 10, 0, kst(2000, 1, 6, 0, 0), null); // 내일 끝(1월 6일 0시) 밖
        groupBuy(product, "이미 지남", "RECRUITING", 0, 10, 0, kst(2000, 1, 4, 8, 59), null);
        groupBuy(product, "연장 끝 모레", "RECRUITING", 0, 10, 0, kst(2000, 1, 5, 9, 0), kst(2000, 1, 6, 9, 0));
        Instant now = kst(2000, 1, 4, 9, 0);

        TodayScheduleGadget gadget = new TodayScheduleGadget(null, store, null, null, null, null);
        DashboardTodayScheduleView v = (DashboardTodayScheduleView) gadget.load(query(now, Permission.COMMERCE_PRODUCT_READ));
        assertThat(v.items()).containsExactly(
                new DashboardTodayScheduleView.Item("GROUPBUY_CLOSE-" + extendedIn, "GROUPBUY_CLOSE", "연장 끝 오늘", null, kst(2000, 1, 4, 18, 0)),
                new DashboardTodayScheduleView.Item("GROUPBUY_CLOSE-" + in, "GROUPBUY_CLOSE", "내일 마감", null, kst(2000, 1, 5, 12, 0)));

        // 다른 항목 권한만(여기선 아무 권한 없음) — 공구 마감은 넣지 않는다
        DashboardTodayScheduleView none = (DashboardTodayScheduleView) gadget.load(query(now));
        assertThat(none.items()).isEmpty();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static DashboardGadgetQuery query(Instant now, Permission... permissions) {
        return query(now, DashboardGadgetQuery.DEFAULT_DAYS, permissions);
    }

    private static DashboardGadgetQuery query(Instant now, int days, Permission... permissions) {
        return new DashboardGadgetQuery(actor(permissions), DashboardGadgetQuery.DEFAULT_LIMIT, days, now);
    }

    private static AdminActor actor(Permission... permissions) {
        return new AdminActor(1L, "시험", "t@test.local", false, java.util.Arrays.stream(permissions).map(Permission::code).collect(java.util.stream.Collectors.toSet()),
                "127.0.0.1");
    }

    private static Messages messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.KOREAN);
        return new Messages(source, new StandardEnvironment(), "messages/messages");
    }

    private static long id() {
        return ThreadLocalRandom.current().nextLong(1L << 60, Long.MAX_VALUE);
    }

    private static String paymentId() {
        StringBuilder id = new StringBuilder("pay_");
        for (int i = 0; i < 32; i++) {
            id.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return id.toString();
    }

    /** 결제 주문 — 결제된 계열이면 결제액(= 청구액)·결제 시각·확정한 길을 채운다 */
    private long order(String type, String status, String anomaly, long amount, Instant paidAt) {
        long id = id();
        boolean paid = Set.of("PAID", "PARTIAL_CANCELED", "CANCELED").contains(status);
        Instant created = paidAt == null ? Instant.parse("2091-01-01T00:00:00Z") : paidAt.minusSeconds(60);
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, currency, status, paid_amount, canceled_amount, anomaly, "
                        + "paid_at, expires_at, confirmed_via, created_at, updated_at) VALUES (?, ?, 1, ?, ?, '시험 결제', ?, 'KRW', ?, ?, 0, ?, ?, ?, ?, ?, ?)",
                id, paymentId(), type, id(), amount, status, paid ? amount : null, anomaly, paid ? Timestamp.from(paidAt) : null, Timestamp.from(created.plusSeconds(300)),
                paid ? "WEBHOOK" : null, Timestamp.from(created), Timestamp.from(created));
        return id;
    }

    private long cancel(long orderId, long amount, String status, String manualMethod, Instant completedAt) {
        long id = id();
        Instant created = completedAt == null ? Instant.parse("2091-01-01T00:00:00Z") : completedAt.minusSeconds(60);
        jdbc.update("INSERT INTO payment_cancels (id, payment_order_id, amount, reason, kind, request_key, status, next_attempt_at, manual_method, created_at, completed_at) "
                        + "VALUES (?, ?, ?, '시험 사유', 'REFUND', ?, ?, ?, ?, ?, ?)", id, orderId, amount, "TEST:" + id, status, Timestamp.from(created), manualMethod,
                Timestamp.from(created), completedAt == null ? null : Timestamp.from(completedAt));
        return id;
    }

    /**
     * 계좌 송금으로 바꾼 공구 환불(결정 67) — 결제 취소(MANUAL · BANK_TRANSFER, 완료 시각 = 전환 시각) · 환불 기록(BANK_TRANSFER — 송금 완료면 COMPLETED) · 송금 대기 줄(GROUP_BUY_REFUND).
     *
     * @return 환불 기록 ID
     */
    private long bankRefund(long groupBuy, long orderId, long amount, Instant convertedAt, String transferStatus, Instant sentAt) {
        long cancel = cancel(orderId, amount, "MANUAL", "BANK_TRANSFER", convertedAt);
        long participation = id();
        Timestamp created = Timestamp.from(convertedAt.minus(Duration.ofDays(1)));
        jdbc.update("INSERT INTO group_buy_participations (id, order_no, group_buy_id, user_id, status, items_amount, shipping_fee, amount, cash_amount, group_buy_revision, "
                        + "payment_order_id, reserved_until, created_at, updated_at) VALUES (?, ?, ?, 1, 'REFUNDED', ?, 0, ?, ?, 1, ?, ?, ?, ?)", participation,
                "G20920520" + String.format("%011d", participation % 100_000_000_000L), groupBuy, amount, amount, amount, orderId, created, created, created);
        long refund = id();
        boolean sent = "SENT".equals(transferStatus);
        jdbc.update("INSERT INTO group_buy_refunds (id, participation_id, group_buy_id, user_id, cause, item_refund, amount, method, payment_order_id, payment_cancel_id, "
                        + "request_key, status, created_at, completed_at) VALUES (?, ?, ?, 1, 'ADMIN', ?, ?, 'BANK_TRANSFER', ?, ?, ?, ?, ?, ?)", refund, participation, groupBuy,
                amount, amount, orderId, cancel, "GB_REFUND:" + refund, sent ? "COMPLETED" : "IN_PROGRESS", created, sent ? Timestamp.from(sentAt) : null);
        transfer("GROUP_BUY_REFUND", refund, amount, transferStatus, sentAt);
        return refund;
    }

    /** 송금 대기 줄 — 계좌 있음(READY)은 계좌 암호문 자리, 송금 완료(SENT)는 계좌를 지운 시각까지 채운다 */
    private void transfer(String sourceKind, long sourceId, long amount, String status, Instant sentAt) {
        boolean sent = "SENT".equals(status);
        jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, user_id, amount, status, account_no_enc, account_erased_at, reason, created_at, sent_by, sent_at) "
                        + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, '시험 송금', ?, ?, ?)", id(), sourceKind, sourceId, amount, status, "READY".equals(status) ? "d1:시험" : null,
                sent ? Timestamp.from(sentAt) : null, Timestamp.from(Instant.parse("2092-01-01T00:00:00Z")), sent ? 1L : null, sent ? Timestamp.from(sentAt) : null);
    }

    private void webhook(String result, Instant at) {
        jdbc.update("INSERT INTO payment_webhook_logs (id, webhook_id, event_type, payment_id, result, http_status, received_at) VALUES (?, ?, 'Transaction.Paid', ?, ?, 200, ?)",
                id(), "wh-" + id(), paymentId(), result, Timestamp.from(at));
    }

    private long product(String name) {
        long category = id();
        Timestamp t = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO group_buy_categories (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)", category, "대시" + (category % 100_000_000L), t, t);
        long id = id();
        jdbc.update("INSERT INTO group_buy_products (id, category_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, category, name, t, t);
        return id;
    }

    /** 공구 한 줄 — 성사형(최소 수량 > 0)은 마감 후 발주, 선착순은 즉시 발송 */
    private long groupBuy(long product, String title, String status, int minQty, int maxQty, int currentQty, Instant endAt, Instant extendedUntil) {
        long id = id();
        boolean draft = "DRAFT".equals(status);
        Instant start = endAt.minus(Duration.ofDays(10));
        jdbc.update("INSERT INTO group_buys (id, product_id, title, group_price, shipping_policy_code, shipping_fee, return_shipping_fee, return_fee_mode, min_qty, max_qty, "
                        + "initial_max_qty, current_qty, start_at, end_at, extended_until, closing_at, fulfillment, ship_planned_at, ship_within_business_days, status, published_at, "
                        + "round_root_id, revision_no, created_at, updated_at) VALUES (?, ?, ?, 10000, 'DEFAULT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, product, title, draft ? null : 3000L, draft ? null : 3000L, draft ? null : "ONE_WAY", minQty, maxQty, draft ? null : maxQty, currentQty, Timestamp.from(start),
                Timestamp.from(endAt), extendedUntil == null ? null : Timestamp.from(extendedUntil), "CLOSING".equals(status) ? Timestamp.from(endAt) : null,
                minQty > 0 ? "AFTER_CLOSE" : "IMMEDIATE", minQty > 0 ? Date.valueOf(LocalDate.of(2000, 3, 1)) : null, minQty > 0 ? null : 3, status,
                draft ? null : Timestamp.from(start), id, draft ? 0 : 1, Timestamp.from(start), Timestamp.from(start));
        return id;
    }

    private long question(long groupBuy, String body, boolean secret, String status, boolean hidden, boolean deleted, Instant at) {
        long id = id();
        jdbc.update("INSERT INTO group_buy_questions (id, group_buy_id, user_id, body, secret, status, hidden_at, hidden_by, hidden_reason, deleted_at, created_at, retain_until) "
                        + "VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, groupBuy, body, secret, status, hidden ? Timestamp.from(at) : null, hidden ? 1L : null,
                hidden ? "시험 숨김 사유" : null, deleted ? Timestamp.from(at) : null, Timestamp.from(at), Date.valueOf(LocalDate.of(2003, 1, 1)));
        return id;
    }
}
