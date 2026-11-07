package com.community.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizApplicationStore.NewOperatorApp;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.dashboard.repository.master.DashboardGadgetStore.AdBooking;
import com.community.dashboard.repository.master.DashboardGadgetStore.AdDay;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대시보드 광고 가젯 읽기(일별 광고 합 · 예약된 광고 건 — JDBC + {@code LEFT JOIN LATERAL} 에서 QueryDSL 상관 서브쿼리로 바꿈)가 예전과 같은 줄을 낸다 — 실제 PostgreSQL.
 * 다른 시험과 같은 DB 라 먼 시각(2093년)에 넣고 트랜잭션을 되돌린다.
 */
class DashboardAdGadgetStoreJpaTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(934);
    private static final Instant NOW = Instant.parse("2093-01-01T00:00:00Z");

    private static TransactionTemplate tx;
    private static JdbcTemplate jdbc;
    private static BizApplicationStore apps;
    private static DashboardGadgetStore store;

    @BeforeAll
    static void wire() {
        tx = new TransactionTemplate(JpaTestContext.transactions());
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        apps = JpaTestContext.create(BizApplicationStore.class);
        store = JpaTestContext.create(DashboardGadgetStore.class);
    }

    /** 예약(수동 — 입금 확인·계약서 1장) 건 + 상품 줄 */
    private static long scheduled(BizApplicationKind kind, Instant start, Instant end, BizProduct... products) {
        long id = IDS.nextId();
        apps.insertOperator(new NewOperatorApp(id, kind, null, null, "광고주", null, null, "광고 " + id, start, end, new Amount(10_000, 1_000, 11_000), 5, 10_000, null,
                1L, 10, NOW));
        for (BizProduct p : products) {
            apps.insertItem(new ItemRow(IDS.nextId(), id, p, 1_000, 10, 10_000, start, end), NOW);
        }
        Amount amount = new Amount(10_000, 1_000, 11_000);
        assertThat(apps.approve(id, BizApplicationStatus.AWAITING_DEPOSIT, start, end, amount, amount, null, BizPaymentMethod.MANUAL, start, 1L, NOW)).isTrue();
        apps.setContractCount(id, 1, NOW);
        apps.setDepositConfirmed(id, NOW, NOW);
        assertThat(apps.move(id, BizApplicationStatus.AWAITING_DEPOSIT, BizApplicationStatus.SCHEDULED, NOW)).isTrue();
        return id;
    }

    @Test
    void 예약된_광고_건은_범위_안에_시작하거나_끝나고_대표_상품은_핫딜_게시가_아닌_이름이_가장_앞선_줄() {
        tx.executeWithoutResult(status -> {
            Instant from = Instant.parse("2093-02-01T00:00:00Z");
            Instant to = Instant.parse("2093-02-08T00:00:00Z");
            long banner = scheduled(BizApplicationKind.AD_BANNER, from.plusSeconds(3_600), Instant.parse("2093-03-01T00:00:00Z"), BizProduct.WIDE_BANNER,
                    BizProduct.STRIP_BANNER, BizProduct.HOTDEAL_POST);
            long endsInside = scheduled(BizApplicationKind.AD_TEXT_LINE, Instant.parse("2093-01-20T00:00:00Z"), to.minusSeconds(60));
            long hotdealPost = scheduled(BizApplicationKind.HOTDEAL_POST, from.plusSeconds(60), to.plusSeconds(3_600), BizProduct.HOTDEAL_POST);
            long outside = scheduled(BizApplicationKind.AD_BANNER, Instant.parse("2093-01-10T00:00:00Z"), Instant.parse("2093-03-10T00:00:00Z"), BizProduct.WIDE_BANNER);
            long startsAtEnd = scheduled(BizApplicationKind.AD_BANNER, to, to.plusSeconds(3_600), BizProduct.WIDE_BANNER);

            List<AdBooking> rows = store.adBookings(from, to);
            assertThat(rows).extracting(AdBooking::id).contains(banner, endsInside).doesNotContain(hotdealPost, outside, startsAtEnd);
            AdBooking b = rows.stream().filter(r -> r.id() == banner).findFirst().orElseThrow();
            assertThat(b.product()).isEqualTo("STRIP_BANNER");
            assertThat(b.title()).isEqualTo("광고 " + banner);
            assertThat(b.startAt()).isEqualTo(from.plusSeconds(3_600));
            assertThat(rows.stream().filter(r -> r.id() == endsInside).findFirst().orElseThrow().product()).as("상품 줄 없음").isNull();
            status.setRollbackOnly();
        });
    }

    @Test
    void 일별_광고_합은_날짜마다_자리를_합치고_값이_없는_날은_빠진다() {
        tx.executeWithoutResult(status -> {
            long app = scheduled(BizApplicationKind.AD_BANNER, Instant.parse("2093-04-01T00:00:00Z"), Instant.parse("2093-04-10T00:00:00Z"), BizProduct.WIDE_BANNER);
            stat(app, 1, LocalDate.of(2093, 4, 1), "HOME", 10, 2);
            stat(app, 1, LocalDate.of(2093, 4, 1), "BOARD", 5, 1);
            stat(app, 2, LocalDate.of(2093, 4, 1), "HOME", 1, 0);
            stat(app, 1, LocalDate.of(2093, 4, 3), "HOME", 7, 3);
            stat(app, 1, LocalDate.of(2093, 4, 5), "HOME", 100, 100); // 범위 밖

            assertThat(store.adDays(LocalDate.of(2093, 4, 1), LocalDate.of(2093, 4, 4))).containsExactly(new AdDay(LocalDate.of(2093, 4, 1), 16, 3),
                    new AdDay(LocalDate.of(2093, 4, 3), 7, 3));
            assertThat(store.adDays(LocalDate.of(2093, 4, 2), LocalDate.of(2093, 4, 2))).isEmpty();
            status.setRollbackOnly();
        });
    }

    private static void stat(long app, int version, LocalDate day, String placement, long impressions, long clicks) {
        jdbc.update("INSERT INTO ad_daily_stats (application_id, version, day, placement, impressions, clicks) VALUES (?, ?, ?, ?, ?, ?)", app, version, Date.valueOf(day),
                placement, impressions, clicks);
    }
}
