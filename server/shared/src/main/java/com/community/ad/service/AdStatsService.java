package com.community.ad.service;

import com.community.ad.dto.AdStatsView;
import com.community.ad.repository.master.AdStatsStore;
import com.community.ad.repository.master.AdStatsStore.StatRow;
import com.community.ad.service.AdStatsCalc.Current;
import com.community.ad.service.AdStatsCalc.Span;
import com.community.ad.service.AdStatsCalc.Window;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizFields;
import com.community.bizapplication.service.BizPeriods;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 건의 광고 성과(ad-performance-contract 규칙 2~6) — 사업자({@code GET /api/business/applications/{id}/stats})와 운영자({@code GET /api/admin/ads/{id}/stats})가 같이 쓴다.
 * 누가 볼 수 있는지(본인 건·장부 건)는 부르는 쪽이 먼저 가린다. 계산은 {@link AdStatsCalc}, 읽기는 {@link AdStatsStore}. 매 요청 합(캐시 없음) — 오늘 숫자는 계속 는다.
 */
@Service
@Transactional(readOnly = true)
public class AdStatsService {

    private final AdStatsStore store;
    private final BizApplicationStore apps;
    private final Clock clock;

    public AdStatsService(AdStatsStore store, BizApplicationStore apps, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.apps = apps;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    public Instant now() {
        return clock.instant();
    }

    /**
     * 한 건의 성과 — 요청 {@code from}·{@code to}(KST 날짜, 끝 포함, 빠지면 null)는 형식이 틀리거나 {@code from > to} 면 400 {@code fields.from}. 노출 자리 줄이 없는 건(노출 없는
     * 핫딜 글)은 빈 결과(범위 = 시작 날짜 하루).
     */
    public AdStatsView.Application application(AppRow app, List<ItemRow> items, String fromText, String toText) {
        LocalDate[] asked = range(fromText, toText);
        Instant now = now();
        LocalDate startDate = BizPeriods.startDate(app.startAt());
        if (BizApplicationReader.capacityProduct(items) == null) {
            return empty(new AdStatsView.Range(startDate.toString(), startDate.toString()), now);
        }
        Window w = AdStatsCalc.window(startDate, endDate(app), ServiceTime.dateOf(now), asked[0], asked[1]);
        AdStatsView.Range range = new AdStatsView.Range(w.from().toString(), w.to().toString());
        if (w.empty()) {
            return empty(range, now);
        }
        List<StatRow> rows = store.rows(List.of(app.id()), w.from(), w.to());
        List<Span> spans = spans(app);
        Map<Integer, Instant> approved = new TreeMap<>();
        for (CreativeRow c : apps.creatives(app.id())) {
            if (c.approvedAt() != null) {
                approved.put(c.version(), c.approvedAt());
            }
        }
        return new AdStatsView.Application(range, now, AdStatsCalc.total(rows), AdStatsCalc.days(w.from(), w.to(), rows, spans), AdStatsCalc.placements(rows),
                AdStatsCalc.versions(approved, rows), AdStatsCalc.downtimes(spans, w.from(), w.to()));
    }

    /**
     * 여러 건의 최근 날짜별 숫자(사업자 센터 요약 — 규칙 8 {@code spark}) — {@code [from, to]} 모든 날짜, 건 기간 밖 날은 0·SHOWING. 건 ID → 날짜 차례 목록.
     */
    public Map<Long, List<AdStatsView.Day>> recent(List<AppRow> list, LocalDate from, LocalDate to) {
        Map<Long, List<StatRow>> byApp = new HashMap<>();
        for (StatRow r : store.rows(list.stream().map(AppRow::id).toList(), from, to)) {
            byApp.computeIfAbsent(r.applicationId(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, List<AdStatsView.Day>> out = new HashMap<>();
        for (AppRow a : list) {
            LocalDate start = BizPeriods.startDate(a.startAt());
            LocalDate end = endDate(a);
            List<Span> spans = spans(a);
            List<AdStatsView.Day> days = new ArrayList<>();
            for (AdStatsView.Day d : AdStatsCalc.days(from, to, byApp.getOrDefault(a.id(), List.of()), spans)) {
                LocalDate date = LocalDate.parse(d.date());
                boolean inside = !date.isBefore(start) && !date.isAfter(end);
                days.add(inside ? d : new AdStatsView.Day(d.date(), 0, 0, AdStatsCalc.SHOWING));
            }
            out.put(a.id(), days);
        }
        return out;
    }

    /** 그 건의 멈춘 구간(처리 기록 + 상태 칸 보정) */
    public List<Span> spans(AppRow a) {
        return AdStatsCalc.spans(store.downtimeEvents(a.id()), new Current(a.status(), a.takedownCause(), a.takenDownAt(), a.closedAt(), a.updatedAt()));
    }

    /** 건 기간의 끝 날짜(KST, 끝 포함) — 승인 뒤 취소된 건은 닫힌 날까지(그 뒤로는 나가지 않는다) */
    static LocalDate endDate(AppRow a) {
        LocalDate end = BizPeriods.endDate(a.endAt());
        if (a.status() == BizApplicationStatus.CANCELED && a.closedAt() != null && a.closedAt().isBefore(a.endAt())) {
            LocalDate closed = ServiceTime.dateOf(a.closedAt());
            if (closed.isBefore(end)) {
                end = closed.isBefore(BizPeriods.startDate(a.startAt())) ? BizPeriods.startDate(a.startAt()) : closed;
            }
        }
        return end;
    }

    /** 요청 범위 — 형식이 틀리거나 {@code from > to} 면 400 {@code fields.from}(규칙 2) */
    private static LocalDate[] range(String fromText, String toText) {
        BizFields f = BizFields.start();
        LocalDate from = date(f, fromText);
        LocalDate to = date(f, toText);
        if (from != null && to != null && from.isAfter(to)) {
            f.add("from", "bizapp.period.invalid");
        }
        f.throwIfAny();
        return new LocalDate[] {from, to};
    }

    private static LocalDate date(BizFields f, String text) {
        String s = BizFields.strip(text);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            f.add("from", "bizapp.period.invalid");
            return null;
        }
    }

    private static AdStatsView.Application empty(AdStatsView.Range range, Instant now) {
        return new AdStatsView.Application(range, now, AdStatsView.Counts.ZERO, List.of(), List.of(), List.of(), List.of());
    }
}
