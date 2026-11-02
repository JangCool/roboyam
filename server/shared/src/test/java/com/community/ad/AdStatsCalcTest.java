package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.dto.AdStatsView;
import com.community.ad.repository.master.AdStatsStore.EventRow;
import com.community.ad.repository.master.AdStatsStore.StatRow;
import com.community.ad.service.AdStatsCalc;
import com.community.ad.service.AdStatsCalc.Current;
import com.community.ad.service.AdStatsCalc.Span;
import com.community.ad.service.AdStatsCalc.Window;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.service.BizPeriods;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * 광고 성과 계산(ad-performance-contract 규칙 2~6, DB 없음) — 범위 자르기 · 멈춘 구간 짝짓기(+ 상태 칸 보정) · 날짜 상태 · 0 채우기 · 자리 · 소재 버전 · 범위와 겹치는 구간.
 */
class AdStatsCalcTest {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 1);

    private static Instant at(LocalDate day, int hour) {
        return BizPeriods.startOf(day).plus(Duration.ofHours(hour));
    }

    private static Current showing() {
        return new Current(BizApplicationStatus.SCHEDULED, null, null, null, at(D1, 0));
    }

    // ── 규칙 2 범위 ──────────────────────────────────────────────────────────

    @Test
    void 범위는_건_기간_교집합_오늘까지로_자르고_빠지면_시작_날짜부터_min_끝_오늘() {
        LocalDate start = D1;
        LocalDate end = D1.plusDays(9);
        // 진행 중 — 빠지면 시작 ~ 오늘
        assertThat(AdStatsCalc.window(start, end, D1.plusDays(3), null, null)).isEqualTo(new Window(start, D1.plusDays(3), false));
        // 끝난 뒤 — 시작 ~ 끝
        assertThat(AdStatsCalc.window(start, end, D1.plusDays(30), null, null)).isEqualTo(new Window(start, end, false));
        // 요청이 기간 밖으로 넘치면 자른다(앞은 시작, 뒤는 min(끝, 오늘))
        assertThat(AdStatsCalc.window(start, end, D1.plusDays(5), D1.minusDays(3), D1.plusDays(20))).isEqualTo(new Window(start, D1.plusDays(5), false));
        assertThat(AdStatsCalc.window(start, end, D1.plusDays(30), D1.plusDays(2), D1.plusDays(4))).isEqualTo(new Window(D1.plusDays(2), D1.plusDays(4), false));
        // 시작 전 — 시작 날짜 하루의 빈 범위
        assertThat(AdStatsCalc.window(start, end, D1.minusDays(1), null, null)).isEqualTo(new Window(start, start, true));
        // 겹치지 않는 요청 — 요청 그대로의 빈 범위
        assertThat(AdStatsCalc.window(start, end, D1.plusDays(30), D1.plusDays(15), D1.plusDays(16))).isEqualTo(new Window(D1.plusDays(15), D1.plusDays(16), true));
    }

    // ── 규칙 4 멈춘 구간 ─────────────────────────────────────────────────────

    @Test
    void 정지_다시켜기_긴급_되돌리기를_짝짓고_정지에서_중단은_그_순간_닫고_새로_연다() {
        List<EventRow> events = List.of(new EventRow(at(D1, 10), "PAUSED", null), new EventRow(at(D1, 12), "RESUMED", "MIN:120"),
                new EventRow(at(D1.plusDays(1), 9), "TAKEN_DOWN", "EMERGENCY"), new EventRow(at(D1.plusDays(1), 18), "RESTORED", "EXTEND · 사유"),
                new EventRow(at(D1.plusDays(2), 8), "PAUSED", "PHONE · 메모"), new EventRow(at(D1.plusDays(3), 8), "STOPPED", "FROM_PAUSE"));
        Current now = new Current(BizApplicationStatus.TAKEN_DOWN, BizTakedownCause.ADVERTISER_STOP, at(D1.plusDays(3), 8), null, at(D1.plusDays(3), 8));
        assertThat(AdStatsCalc.spans(events, now)).containsExactly(new Span(at(D1, 10), at(D1, 12), BizTakedownCause.ADVERTISER_PAUSE),
                new Span(at(D1.plusDays(1), 9), at(D1.plusDays(1), 18), BizTakedownCause.EMERGENCY),
                new Span(at(D1.plusDays(2), 8), at(D1.plusDays(3), 8), BizTakedownCause.ADVERTISER_PAUSE),
                new Span(at(D1.plusDays(3), 8), null, BizTakedownCause.ADVERTISER_STOP));
    }

    @Test
    void 사업자_정지_내림의_까닭은_note_이고_취소가_구간을_닫는다() {
        List<EventRow> events = List.of(new EventRow(at(D1, 9), "TAKEN_DOWN", "SUSPENDED"), new EventRow(at(D1, 11), "CANCELED", "SUSPENDED"));
        Current canceled = new Current(BizApplicationStatus.CANCELED, BizTakedownCause.SUSPENDED, at(D1, 9), at(D1, 11), at(D1, 11));
        assertThat(AdStatsCalc.spans(events, canceled)).containsExactly(new Span(at(D1, 9), at(D1, 11), BizTakedownCause.SUSPENDED));
    }

    @Test
    void 짝이_안_맞으면_상태_칸으로_보정한다() {
        // 기록 없이 지금 내림 — 내린 시각부터 열린 구간
        Current down = new Current(BizApplicationStatus.TAKEN_DOWN, BizTakedownCause.SUSPENDED, at(D1, 7), null, at(D1, 7));
        assertThat(AdStatsCalc.spans(List.of(), down)).containsExactly(new Span(at(D1, 7), null, BizTakedownCause.SUSPENDED));
        // 열린 기록의 까닭이 상태 칸과 다르면 상태 칸을 따른다
        assertThat(AdStatsCalc.spans(List.of(new EventRow(at(D1, 7), "TAKEN_DOWN", null)), down))
                .containsExactly(new Span(at(D1, 7), null, BizTakedownCause.SUSPENDED));
        // 내림이 아닌데 열려 있으면 마지막 변경 시각에 닫는다 · 끝 기록만 있는 것은 버린다
        Current restored = new Current(BizApplicationStatus.SCHEDULED, null, null, null, at(D1, 15));
        assertThat(AdStatsCalc.spans(List.of(new EventRow(at(D1, 5), "RESTORED", null), new EventRow(at(D1, 9), "PAUSED", null)), restored))
                .containsExactly(new Span(at(D1, 9), at(D1, 15), BizTakedownCause.ADVERTISER_PAUSE));
        // 기록 없는 TAKEN_DOWN 의 note 를 모르면 긴급 내림
        assertThat(AdStatsCalc.spans(List.of(new EventRow(at(D1, 1), "TAKEN_DOWN", "??"), new EventRow(at(D1, 2), "RESTORED", null)), showing()))
                .containsExactly(new Span(at(D1, 1), at(D1, 2), BizTakedownCause.EMERGENCY));
    }

    // ── 규칙 3 날짜 상태 ─────────────────────────────────────────────────────

    @Test
    void 그날_일부라도_정지면_PAUSED_그_밖의_내림이면_TAKEN_DOWN_둘_다면_TAKEN_DOWN() {
        List<Span> spans = List.of(new Span(at(D1, 23), at(D1.plusDays(1), 1), BizTakedownCause.ADVERTISER_PAUSE),
                new Span(at(D1.plusDays(1), 10), at(D1.plusDays(1), 11), BizTakedownCause.EMERGENCY),
                new Span(at(D1.plusDays(3), 0), null, BizTakedownCause.ADVERTISER_STOP));
        assertThat(AdStatsCalc.state(D1.minusDays(1), spans)).isEqualTo("SHOWING");
        assertThat(AdStatsCalc.state(D1, spans)).isEqualTo("PAUSED");
        assertThat(AdStatsCalc.state(D1.plusDays(1), spans)).isEqualTo("TAKEN_DOWN"); // 정지 + 긴급
        assertThat(AdStatsCalc.state(D1.plusDays(2), spans)).isEqualTo("SHOWING");
        // 끝이 정확히 다음 날 0시면 그날만 — 열린 구간은 뒤의 모든 날
        assertThat(AdStatsCalc.state(D1.plusDays(2), List.of(new Span(at(D1.plusDays(1), 3), at(D1.plusDays(2), 0), BizTakedownCause.SUSPENDED))))
                .isEqualTo("SHOWING");
        assertThat(AdStatsCalc.state(D1.plusDays(9), spans)).isEqualTo("TAKEN_DOWN");
    }

    // ── 0 채우기 · 자리 · 버전 · 구간 ─────────────────────────────────────────

    @Test
    void 날짜는_범위_안_모두_0을_채우고_버전_자리를_합친다() {
        List<StatRow> rows = List.of(new StatRow(1, 1, D1, "HOME", 10, 1), new StatRow(1, 1, D1, "BOARD", 5, 0), new StatRow(1, 2, D1.plusDays(2), "HOME", 7, 2));
        List<AdStatsView.Day> days = AdStatsCalc.days(D1, D1.plusDays(3), rows, List.of(new Span(at(D1.plusDays(1), 3), at(D1.plusDays(1), 4),
                BizTakedownCause.ADVERTISER_PAUSE)));
        assertThat(days).containsExactly(new AdStatsView.Day("2026-11-01", 15, 1, "SHOWING"), new AdStatsView.Day("2026-11-02", 0, 0, "PAUSED"),
                new AdStatsView.Day("2026-11-03", 7, 2, "SHOWING"), new AdStatsView.Day("2026-11-04", 0, 0, "SHOWING"));
        assertThat(AdStatsCalc.total(rows)).isEqualTo(new AdStatsView.Counts(22, 3));
    }

    @Test
    void 자리는_0을_빼고_노출_많은_차례() {
        List<StatRow> rows = List.of(new StatRow(1, 1, D1, "BOARD", 3, 0), new StatRow(1, 1, D1, "HOME", 2, 1), new StatRow(1, 1, D1.plusDays(1), "HOME", 4, 0),
                new StatRow(1, 1, D1, "WING_LEFT", 0, 0), new StatRow(1, 1, D1, "PAGE", 0, 1));
        assertThat(AdStatsCalc.placements(rows)).containsExactly(new AdStatsView.PlacementRow("HOME", 6, 1), new AdStatsView.PlacementRow("BOARD", 3, 0),
                new AdStatsView.PlacementRow("PAGE", 0, 1));
    }

    @Test
    void 버전은_승인된_것_전부와_집계만_있는_버전_current_는_최신_승인() {
        Map<Integer, Instant> approved = new TreeMap<>(Map.of(1, at(D1, 9), 2, at(D1.plusDays(2), 9)));
        List<StatRow> rows = List.of(new StatRow(1, 2, D1.plusDays(3), "HOME", 8, 1), new StatRow(1, 3, D1.plusDays(3), "HOME", 1, 0));
        assertThat(AdStatsCalc.versions(approved, rows)).containsExactly(new AdStatsView.VersionRow(1, at(D1, 9), false, 0, 0),
                new AdStatsView.VersionRow(2, at(D1.plusDays(2), 9), true, 8, 1), new AdStatsView.VersionRow(3, null, false, 1, 0));
    }

    @Test
    void 구간은_범위와_겹치는_것만_오래된_차례() {
        List<Span> spans = List.of(new Span(at(D1.plusDays(5), 0), null, BizTakedownCause.ADVERTISER_STOP),
                new Span(at(D1.minusDays(3), 0), at(D1.minusDays(2), 0), BizTakedownCause.EMERGENCY),
                new Span(at(D1, 23), at(D1.plusDays(1), 2), BizTakedownCause.ADVERTISER_PAUSE));
        assertThat(AdStatsCalc.downtimes(spans, D1, D1.plusDays(5))).containsExactly(new AdStatsView.Downtime(at(D1, 23), at(D1.plusDays(1), 2), "ADVERTISER_PAUSE"),
                new AdStatsView.Downtime(at(D1.plusDays(5), 0), null, "ADVERTISER_STOP"));
        assertThat(AdStatsCalc.downtimes(spans, D1.plusDays(1).plusDays(1), D1.plusDays(4))).isEmpty();
    }
}
