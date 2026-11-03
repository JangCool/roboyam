package com.community.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.dashboard.dto.SalesPeriods;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * 매출 요약 기간(admin-dashboard-contract 「SALES_SUMMARY」 · commerce.md 3절 「날짜 판단은 KST」) — 오늘·어제·이번 달·지난달 같은 기간의 한국 시간 경계.
 */
class SalesPeriodsTest {

    static Instant kst(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, ServiceTime.ZONE).toInstant();
    }

    @Test
    void 날짜_경계는_한국_시간_0시다_UTC_전날_15시() {
        SalesPeriods p = SalesPeriods.of(kst(2091, 3, 15, 10, 0));
        assertThat(p.today().from()).isEqualTo(Instant.parse("2091-03-14T15:00:00Z"));
        assertThat(p.today().to()).isEqualTo(Instant.parse("2091-03-15T15:00:00Z"));
        assertThat(p.yesterday().from()).isEqualTo(kst(2091, 3, 14, 0, 0));
        assertThat(p.yesterday().to()).isEqualTo(p.today().from());
        // 한국 0시 30분(UTC 로는 전날)은 오늘이다
        assertThat(p.today().contains(kst(2091, 3, 15, 0, 30))).isTrue();
        assertThat(p.yesterday().contains(kst(2091, 3, 15, 0, 30))).isFalse();
    }

    @Test
    void 이번_달은_1일_0시부터_오늘까지_지난달은_같은_일_같은_시각까지() {
        SalesPeriods p = SalesPeriods.of(kst(2091, 3, 15, 10, 0));
        assertThat(p.month().from()).isEqualTo(kst(2091, 3, 1, 0, 0));
        assertThat(p.month().to()).isEqualTo(kst(2091, 3, 16, 0, 0));
        assertThat(p.lastMonth().from()).isEqualTo(kst(2091, 2, 1, 0, 0));
        assertThat(p.lastMonth().to()).isEqualTo(kst(2091, 2, 15, 10, 0));
    }

    @Test
    void 지난달이_짧으면_지난달_끝에서_멈춘다() {
        SalesPeriods p = SalesPeriods.of(kst(2091, 3, 30, 10, 0));
        assertThat(p.lastMonth().from()).isEqualTo(kst(2091, 2, 1, 0, 0));
        assertThat(p.lastMonth().to()).isEqualTo(kst(2091, 3, 1, 0, 0));
        // 3월 28일 10시는 아직 2월 안 — 2월 28일 10시까지
        assertThat(SalesPeriods.of(kst(2091, 3, 28, 10, 0)).lastMonth().to()).isEqualTo(kst(2091, 2, 28, 10, 0));
    }

    @Test
    void 매달_1일에는_어제가_지난달이고_1월의_지난달은_작년_12월() {
        SalesPeriods first = SalesPeriods.of(kst(2091, 3, 1, 0, 30));
        assertThat(first.yesterday().from()).isEqualTo(kst(2091, 2, 28, 0, 0));
        assertThat(first.month().from()).isEqualTo(kst(2091, 3, 1, 0, 0));
        assertThat(first.lastMonth().to()).isEqualTo(kst(2091, 2, 1, 0, 30));

        SalesPeriods january = SalesPeriods.of(kst(2091, 1, 10, 9, 0));
        assertThat(january.lastMonth().from()).isEqualTo(kst(2090, 12, 1, 0, 0));
        assertThat(january.lastMonth().to()).isEqualTo(kst(2090, 12, 10, 9, 0));
    }
}
