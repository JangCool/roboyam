package com.community.ad.service;

import com.community.ad.dto.AdStatsView;
import com.community.ad.repository.master.AdStatsStore.EventRow;
import com.community.ad.repository.master.AdStatsStore.StatRow;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.service.BizPeriods;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 광고 성과 계산(ad-performance-contract 규칙 2~6) — DB 없이 시험할 수 있게 값만 받는다. 날짜는 모두 한국 날짜(KST), 구간은 반열림 {@code [from, to)} 순간.
 */
public final class AdStatsCalc {

    public static final String SHOWING = "SHOWING";
    public static final String PAUSED = "PAUSED";
    public static final String TAKEN_DOWN = "TAKEN_DOWN";

    private AdStatsCalc() {
    }

    /** 센 범위 — {@code empty} 면 센 날이 없다(시작 전·건 기간과 겹치지 않는 요청) */
    public record Window(LocalDate from, LocalDate to, boolean empty) {
    }

    /** 멈춘 구간 — {@code to} null 이면 지금도 멈춰 있다 */
    public record Span(Instant from, Instant to, BizTakedownCause cause) {

        boolean overlaps(Instant start, Instant end) {
            return from.isBefore(end) && (to == null || to.isAfter(start));
        }
    }

    /** 처리 기록과 어긋날 때 보정할 지금 상태 칸 */
    public record Current(BizApplicationStatus status, BizTakedownCause cause, Instant takenDownAt, Instant closedAt, Instant updatedAt) {
    }

    /**
     * 규칙 2 — 요청 {@code from}·{@code to}(null = 빠짐)를 건 기간 날짜 {@code [startDate, endDate]} ∩ 오늘까지로 자른다. 빠지면 시작 날짜 ~ min(끝 날짜, 오늘).
     * 시작 전(오늘 &lt; 시작 날짜)이면 시작 날짜 하루의 빈 범위. 잘라서 남는 날이 없으면 요청 그대로의 빈 범위.
     */
    public static Window window(LocalDate startDate, LocalDate endDate, LocalDate today, LocalDate from, LocalDate to) {
        if (today.isBefore(startDate)) {
            return new Window(startDate, startDate, true);
        }
        LocalDate last = endDate.isBefore(today) ? endDate : today;
        LocalDate lo = from == null || from.isBefore(startDate) ? startDate : from;
        LocalDate hi = to == null || to.isAfter(last) ? last : to;
        if (lo.isAfter(hi)) {
            return new Window(from == null ? startDate : from, to == null ? last : to, true);
        }
        return new Window(lo, hi, false);
    }

    /**
     * 규칙 4 — 처리 기록(오래된 차례)을 짝짓는다: 시작 {@code TAKEN_DOWN}(까닭 = note, 모르면 EMERGENCY)·{@code PAUSED}·{@code STOPPED}, 끝 {@code RESTORED}·{@code RESUMED}·
     * {@code CANCELED}·{@code FORFEITED}. 열린 구간이 있는데 새 시작이 오면(정지 → 중단 등) 그 순간에 닫고 새로 연다. 짝이 안 맞으면 지금 상태 칸으로 보정한다 —
     * 지금 내림인데 열린 구간이 없으면 {@code takenDownAt} 부터 열고(까닭이 다르면 고친다), 내림이 아닌데 열려 있으면 닫은 시각(없으면 마지막 변경 시각)에 닫는다.
     */
    public static List<Span> spans(List<EventRow> events, Current current) {
        List<Span> out = new ArrayList<>();
        Instant openFrom = null;
        BizTakedownCause openCause = null;
        for (EventRow e : events) {
            BizTakedownCause starts = switch (e.action()) {
                case "TAKEN_DOWN" -> causeOf(e.note());
                case "PAUSED" -> BizTakedownCause.ADVERTISER_PAUSE;
                case "STOPPED" -> BizTakedownCause.ADVERTISER_STOP;
                default -> null;
            };
            if (openFrom != null) {
                out.add(new Span(openFrom, e.at(), openCause));
                openFrom = null;
                openCause = null;
            }
            if (starts != null) {
                openFrom = e.at();
                openCause = starts;
            }
        }
        boolean down = current.status() == BizApplicationStatus.TAKEN_DOWN && current.cause() != null;
        if (down) {
            if (openFrom == null) {
                Instant from = current.takenDownAt() != null ? current.takenDownAt() : current.updatedAt();
                // 닫힌 구간 뒤에 열어야 차례가 맞는다
                if (!out.isEmpty() && from.isBefore(out.getLast().to())) {
                    from = out.getLast().to();
                }
                out.add(new Span(from, null, current.cause()));
            } else {
                out.add(new Span(openFrom, null, current.cause()));
            }
        } else if (openFrom != null) {
            Instant to = current.closedAt() != null ? current.closedAt() : current.updatedAt();
            out.add(new Span(openFrom, to == null || to.isBefore(openFrom) ? openFrom : to, openCause));
        }
        return out;
    }

    /** 규칙 3 — 그날(KST)에 정지 구간이 걸치면 PAUSED, 그 밖의 내림이 걸치면 TAKEN_DOWN(둘 다면 TAKEN_DOWN), 아니면 SHOWING */
    public static String state(LocalDate day, List<Span> spans) {
        Instant start = BizPeriods.startOf(day);
        Instant end = BizPeriods.startOf(day.plusDays(1));
        boolean paused = false;
        for (Span s : spans) {
            if (!s.overlaps(start, end)) {
                continue;
            }
            if (s.cause() == BizTakedownCause.ADVERTISER_PAUSE) {
                paused = true;
            } else {
                return TAKEN_DOWN;
            }
        }
        return paused ? PAUSED : SHOWING;
    }

    /** 범위 안 모든 날짜(0 포함, 오래된 차례) — 상태는 {@link #state} */
    public static List<AdStatsView.Day> days(LocalDate from, LocalDate to, List<StatRow> rows, List<Span> spans) {
        Map<LocalDate, AdStatsView.Counts> sums = new TreeMap<>();
        for (StatRow r : rows) {
            sums.merge(r.day(), new AdStatsView.Counts(r.impressions(), r.clicks()), AdStatsView.Counts::plus);
        }
        List<AdStatsView.Day> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AdStatsView.Counts c = sums.getOrDefault(d, AdStatsView.Counts.ZERO);
            out.add(new AdStatsView.Day(d.toString(), c.impressions(), c.clicks(), state(d, spans)));
        }
        return out;
    }

    /** 규칙 6 — 자리별 합, 0 인 자리는 빼고 노출 많은 차례(같으면 클릭 많은 차례 · 자리 이름) */
    public static List<AdStatsView.PlacementRow> placements(List<StatRow> rows) {
        Map<String, AdStatsView.Counts> sums = new LinkedHashMap<>();
        for (StatRow r : rows) {
            sums.merge(r.placement(), new AdStatsView.Counts(r.impressions(), r.clicks()), AdStatsView.Counts::plus);
        }
        return sums.entrySet().stream().filter(e -> !e.getValue().empty())
                .map(e -> new AdStatsView.PlacementRow(e.getKey(), e.getValue().impressions(), e.getValue().clicks()))
                .sorted(Comparator.comparingLong(AdStatsView.PlacementRow::impressions).reversed()
                        .thenComparing(Comparator.comparingLong(AdStatsView.PlacementRow::clicks).reversed())
                        .thenComparing(AdStatsView.PlacementRow::placement))
                .toList();
    }

    /**
     * 규칙 5 — 승인된 버전 전부(0 포함)와 집계 줄만 있는 버전(승인 기록 없음 — {@code approvedAt} null), 버전 차례. {@code current} = 최신 승인 버전.
     *
     * @param approved 버전 → 승인 시각(승인된 것만)
     */
    public static List<AdStatsView.VersionRow> versions(Map<Integer, Instant> approved, List<StatRow> rows) {
        Map<Integer, AdStatsView.Counts> sums = new TreeMap<>();
        approved.keySet().forEach(v -> sums.put(v, AdStatsView.Counts.ZERO));
        for (StatRow r : rows) {
            sums.merge(r.version(), new AdStatsView.Counts(r.impressions(), r.clicks()), AdStatsView.Counts::plus);
        }
        Integer current = approved.keySet().stream().max(Integer::compare).orElse(null);
        return sums.entrySet().stream().map(e -> new AdStatsView.VersionRow(e.getKey(), approved.get(e.getKey()), e.getKey().equals(current),
                e.getValue().impressions(), e.getValue().clicks())).toList();
    }

    /** 범위 {@code [from, to]}(KST 날짜)와 겹치는 구간만(오래된 차례) */
    public static List<AdStatsView.Downtime> downtimes(List<Span> spans, LocalDate from, LocalDate to) {
        Instant start = BizPeriods.startOf(from);
        Instant end = BizPeriods.startOf(to.plusDays(1));
        return spans.stream().filter(s -> s.overlaps(start, end)).sorted(Comparator.comparing(Span::from))
                .map(s -> new AdStatsView.Downtime(s.from(), s.to(), s.cause().name())).toList();
    }

    /** 합계 */
    public static AdStatsView.Counts total(List<StatRow> rows) {
        AdStatsView.Counts c = AdStatsView.Counts.ZERO;
        for (StatRow r : rows) {
            c = c.plus(r.impressions(), r.clicks());
        }
        return c;
    }

    private static BizTakedownCause causeOf(String note) {
        if (note != null) {
            String head = note.split(" · ", 2)[0].strip();
            for (BizTakedownCause c : BizTakedownCause.values()) {
                if (c.name().equals(head)) {
                    return c;
                }
            }
        }
        return BizTakedownCause.EMERGENCY;
    }
}
