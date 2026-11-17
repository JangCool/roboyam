package com.community.installation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 데모 설치의 기간 글자 칸 옮기기({@link DemoDataInstaller#periodShift} · {@link DemoDataInstaller#shiftPeriod}) — 칭호 기간(이달의 작가 'YYYY-MM' · 올해의 작가 'YYYY')은
 * 시각 칸이 아니라 글자라 시각 옮기기가 건드리지 않았다(늦게 설치하면 「10월의 작가」가 옛 달). 팩 전체에 같은 달 수·해 수만큼 옮긴다(이웃 달 줄이 한 달로 모이지 않게). DB 없음.
 */
class DemoPeriodShiftTest {

    /** 팩을 만든 때 — 2026-11-17 14:40 한국 시간 */
    private static final Instant BUILT = Instant.parse("2026-11-17T05:40:17Z");

    private static long days(double d) {
        return (long) (d * 86_400_000L);
    }

    @Test
    void 기간은_팩_만든_달에서_옮긴_달까지의_차이만큼_옮긴다() {
        // 같은 달 안에서 설치 — 그대로
        DemoDataInstaller.PeriodShift same = DemoDataInstaller.periodShift(BUILT, days(5));
        assertThat(same.months()).isZero();
        assertThat(DemoDataInstaller.shiftPeriod("2026-10", same)).isEqualTo("2026-10");

        // 두 달 뒤(2027-01) 설치 — 해를 넘긴다
        DemoDataInstaller.PeriodShift later = DemoDataInstaller.periodShift(BUILT, days(60));
        assertThat(later.months()).isEqualTo(2);
        assertThat(later.years()).isEqualTo(1);
        assertThat(DemoDataInstaller.shiftPeriod("2026-10", later)).isEqualTo("2026-12");
        assertThat(DemoDataInstaller.shiftPeriod("2026-11", later)).isEqualTo("2027-01");
        assertThat(DemoDataInstaller.shiftPeriod("2025", later)).isEqualTo("2026");
        // 빈 값(기간 없는 칭호)·모르는 모양은 그대로
        assertThat(DemoDataInstaller.shiftPeriod("", later)).isEmpty();
        assertThat(DemoDataInstaller.shiftPeriod(null, later)).isNull();
        assertThat(DemoDataInstaller.shiftPeriod("2026-13", later)).isEqualTo("2026-13");

        // 1년 넘게 뒤 — 달 수가 12를 넘어도 맞다
        DemoDataInstaller.PeriodShift far = DemoDataInstaller.periodShift(BUILT, days(400));
        assertThat(DemoDataInstaller.shiftPeriod("2026-08", far)).isEqualTo(java.time.YearMonth.of(2026, 8).plusMonths(far.months()).toString());
        assertThat(far.months()).isEqualTo(13);
    }

    @Test
    void 달은_한국_시간_달력으로_센다() {
        // 11-30 23:30 한국 시간에 만든 팩을 1시간 뒤 설치 — 한국 시간으로 12월(UTC 로는 아직 11월)
        Instant built = Instant.parse("2026-11-30T14:30:00Z");
        DemoDataInstaller.PeriodShift s = DemoDataInstaller.periodShift(built, Duration.ofHours(1).toMillis());
        assertThat(s.months()).isEqualTo(1);
        assertThat(DemoDataInstaller.shiftPeriod("2026-10", s)).isEqualTo("2026-11");
    }

    @Test
    void 이웃_달_줄은_옮긴_뒤에도_서로_다른_달이다() {
        // 데모 팩 모양: 같은 회원이 8·9·10월 이달의 작가(받은 날 9/2·10/2·11/2). 줄마다 받은 날로 셈하면 29.6일 옮길 때 8월·9월이 둘 다 9월이 된다 — 팩 전체 한 값으로 옮겨 겹치지 않는다
        DemoDataInstaller.PeriodShift s = DemoDataInstaller.periodShift(BUILT, days(29.6));
        assertThat(java.util.stream.Stream.of("2026-08", "2026-09", "2026-10").map(p -> DemoDataInstaller.shiftPeriod(p, s)).distinct()).hasSize(3);
        assertThat(DemoDataInstaller.shiftPeriod("2026-10", s)).isEqualTo("2026-11");
    }

    @Test
    void 기간_칸은_칭호_줄과_이달의_작가_후보_달() {
        assertThat(DemoDataInstaller.PERIOD_COLUMNS).containsEntry("member_titles", "period").containsEntry("title_monthly_candidates", "month");
    }
}
