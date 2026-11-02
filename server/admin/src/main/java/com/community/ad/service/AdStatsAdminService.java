package com.community.ad.service;

import com.community.ad.dto.AdAdminView;
import com.community.ad.dto.AdStatsAdminView;
import com.community.ad.dto.AdStatsView;
import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import com.community.ad.repository.master.AdStatsAdminStore;
import com.community.ad.repository.master.AdStatsAdminStore.BookingSum;
import com.community.ad.repository.master.AdStatsAdminStore.SumRow;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizApplicationReader.Snapshot;
import com.community.bizapplication.service.BizFields;
import com.community.bizapplication.service.BizPeriods;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 광고 성과(admin-web {@code /ads/performance}·일정표 서랍 「성과」 탭, ad-performance-contract 규칙 9~11) — 권한 {@code ad.read} 는 컨트롤러가 본다. 한 건 계산은 shared
 * {@link AdStatsService}, 합계·건별 읽기는 {@link AdStatsAdminStore}. 줄은 일정표 {@link AdAdminView.Row} 그대로(광고주 ID {@code adv-}·{@code biz-} 규칙 포함).
 */
@Service
@Transactional(readOnly = true)
public class AdStatsAdminService {

    /** 범위 최대 날 수(끝 포함) */
    static final int MAX_DAYS = 366;
    static final int TOP_ADVERTISERS = 10;
    static final List<String> SORTS = List.of("impressions", "clicks", "ctr");

    private final AdStatsService stats;
    private final AdStatsAdminStore store;
    private final BizApplicationReader reader;
    private final BizApplicationStore apps;
    private final AdAdvertiserStore advertisers;

    public AdStatsAdminService(AdStatsService stats, AdStatsAdminStore store, BizApplicationReader reader, BizApplicationStore apps, AdAdvertiserStore advertisers) {
        this.stats = stats;
        this.store = store;
        this.reader = reader;
        this.apps = apps;
        this.advertisers = advertisers;
    }

    /** 거르기(범위 · 유형 · 광고주) */
    record Query(LocalDate from, LocalDate to, BizProduct type, AdAdvertiserService.Ref advertiser, String advertiserId) {
    }

    // ── 한 건 ───────────────────────────────────────────────────────────────

    /** 장부 건 한 건(규칙 11) — 없거나 장부 유형 줄이 없으면 404 */
    public AdStatsView.Application application(String idText, String from, String to) {
        Snapshot s = reader.snapshot(AdAdminService.parseId(idText));
        if (BizApplicationReader.capacityProduct(s.items()) == null) {
            throw BizApplicationException.notFound();
        }
        return stats.application(s.app(), s.items(), from, to);
    }

    // ── 합계 ────────────────────────────────────────────────────────────────

    /** 합계·날짜별·유형별·자리별·광고주 상위 10(규칙 9) */
    public AdStatsAdminView.Overview overview(String fromText, String toText, String typeText, String advertiserText) {
        BizFields f = BizFields.start();
        Query q = query(f, fromText, toText, typeText, advertiserText);
        f.throwIfAny();
        Instant now = stats.now();
        List<SumRow> rows = store.sums(q.from(), q.to(), q.type(), q.advertiser());

        Map<LocalDate, long[]> days = new LinkedHashMap<>();
        for (LocalDate d = q.from(); !d.isAfter(q.to()); d = d.plusDays(1)) {
            days.put(d, new long[2]);
        }
        Map<Long, long[]> perApp = new HashMap<>();
        Map<Long, BizProduct> typeOf = new HashMap<>();
        Map<String, long[]> perPlacement = new LinkedHashMap<>();
        for (SumRow r : rows) {
            add(days.get(r.day()), r.impressions(), r.clicks());
            add(perApp.computeIfAbsent(r.applicationId(), k -> new long[2]), r.impressions(), r.clicks());
            typeOf.put(r.applicationId(), r.product());
            add(perPlacement.computeIfAbsent(r.placement(), k -> new long[2]), r.impressions(), r.clicks());
        }

        long impressions = 0;
        long clicks = 0;
        long shown = 0;
        Map<BizProduct, long[]> perType = new LinkedHashMap<>();
        for (Map.Entry<Long, long[]> e : perApp.entrySet()) {
            long[] c = e.getValue();
            impressions += c[0];
            clicks += c[1];
            shown += c[0] > 0 ? 1 : 0;
            long[] t = perType.computeIfAbsent(typeOf.get(e.getKey()), k -> new long[3]);
            add(t, c[0], c[1]);
            t[2] += c[0] > 0 ? 1 : 0;
        }

        List<AdStatsAdminView.DayTotal> dayRows = days.entrySet().stream().map(e -> new AdStatsAdminView.DayTotal(e.getKey().toString(), e.getValue()[0],
                e.getValue()[1])).toList();
        List<AdStatsAdminView.TypeRow> typeRows = perType.entrySet().stream().filter(e -> nonZero(e.getValue()))
                .map(e -> new AdStatsAdminView.TypeRow(e.getKey().name(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingLong(AdStatsAdminView.TypeRow::impressions).reversed()
                        .thenComparing(Comparator.comparingLong(AdStatsAdminView.TypeRow::clicks).reversed()).thenComparing(AdStatsAdminView.TypeRow::type))
                .toList();
        List<AdStatsView.PlacementRow> placementRows = perPlacement.entrySet().stream().filter(e -> nonZero(e.getValue()))
                .map(e -> new AdStatsView.PlacementRow(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(AdStatsView.PlacementRow::impressions).reversed()
                        .thenComparing(Comparator.comparingLong(AdStatsView.PlacementRow::clicks).reversed()).thenComparing(AdStatsView.PlacementRow::placement))
                .toList();
        return new AdStatsAdminView.Overview(now, new AdStatsView.Range(q.from().toString(), q.to().toString()), filteredAdvertiser(q),
                new AdStatsAdminView.Totals(impressions, clicks, shown), dayRows, typeRows, placementRows, topAdvertisers(perApp));
    }

    // ── 건별 ────────────────────────────────────────────────────────────────

    /** 건별 표(규칙 10) — 정렬 {@code impressions|clicks|ctr}(기본 impressions) · {@code asc|desc}(기본 desc) · 쪽 {@code page} 0부터 · {@code size} 기본 20 최대 100 */
    public AdStatsAdminView.BookingPage bookings(String fromText, String toText, String typeText, String advertiserText, String sortText, String dirText,
                                                 String pageText, String sizeText) {
        BizFields f = BizFields.start();
        Query q = query(f, fromText, toText, typeText, advertiserText);
        String sort = BizFields.strip(sortText) == null ? "impressions" : f.oneOf("sort", sortText, s -> SORTS.contains(s) ? s : null);
        String dir = BizFields.strip(dirText) == null ? "desc" : f.oneOf("dir", dirText, s -> s.equals("asc") || s.equals("desc") ? s : null);
        int page = number(f, "page", pageText, 0, 0, Integer.MAX_VALUE);
        int size = number(f, "size", sizeText, 20, 1, AdAdminService.MAX_PAGE_SIZE);
        f.throwIfAny();
        Instant now = stats.now();
        List<BookingSum> sums = new ArrayList<>(store.bookings(q.from(), q.to(), BizPeriods.startOf(q.from()), BizPeriods.startOf(q.to().plusDays(1)), q.type(),
                q.advertiser(), now));
        sums.sort(order(sort, "asc".equals(dir)));
        int start = (int) Math.min(sums.size(), (long) page * size);
        List<BookingSum> slice = sums.subList(start, Math.min(sums.size(), start + size));
        Map<Long, AppRow> byId = new HashMap<>();
        apps.findAll(slice.stream().map(BookingSum::applicationId).toList()).forEach(a -> byId.put(a.id(), a));
        Map<Long, AdvertiserRow> advs = advertiserRows(byId.values());
        List<AdStatsAdminView.BookingRow> items = new ArrayList<>();
        for (BookingSum b : slice) {
            AppRow a = byId.get(b.applicationId());
            if (a == null) {
                continue;
            }
            AdAdminView.Row row = AdAdminService.row(b.product(), a, AdAdminService.advertiserOf(a, a.advertiserId() == null ? null : advs.get(a.advertiserId())), now);
            items.add(AdStatsAdminView.BookingRow.of(row, b.impressions(), b.clicks()));
        }
        return new AdStatsAdminView.BookingPage(items, sums.size());
    }

    /**
     * 건별 정렬 — 노출·클릭은 그 값, 클릭률은 클릭 ÷ 노출(노출 0 은 방향과 상관없이 뒤). 같으면 id 내림차순.
     */
    static Comparator<BookingSum> order(String sort, boolean asc) {
        Comparator<BookingSum> byId = Comparator.comparingLong(BookingSum::applicationId).reversed();
        if ("ctr".equals(sort)) {
            // 둘 다 노출 > 0 일 때만 부른다 — x.clicks/x.imp 와 y.clicks/y.imp 를 곱해서 견준다(소수 오차 없이)
            Comparator<BookingSum> ctr = (x, y) -> Long.compare(x.clicks() * y.impressions(), y.clicks() * x.impressions());
            Comparator<BookingSum> directed = asc ? ctr : ctr.reversed();
            Comparator<BookingSum> zeroLast = Comparator.comparing((BookingSum b) -> b.impressions() == 0);
            return zeroLast.thenComparing((x, y) -> x.impressions() == 0 && y.impressions() == 0 ? 0 : directed.compare(x, y)).thenComparing(byId);
        }
        Comparator<BookingSum> value = "clicks".equals(sort) ? Comparator.comparingLong(BookingSum::clicks) : Comparator.comparingLong(BookingSum::impressions);
        return (asc ? value : value.reversed()).thenComparing(byId);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 범위 필수(KST 날짜, 끝 포함) · from &gt; to 400 fields.from · 366일 넘으면 400 fields.to · 유형 8 · 광고주 ID 모양 */
    private static Query query(BizFields f, String fromText, String toText, String typeText, String advertiserText) {
        LocalDate from = f.date("from", fromText);
        LocalDate to = f.date("to", toText);
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                f.add("from", "bizapp.period.invalid");
            } else if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
                f.add("to", "bizapp.period.range", MAX_DAYS);
            }
        }
        BizProduct type = BizFields.strip(typeText) == null ? null : f.oneOf("type", typeText, s -> {
            BizProduct p = BizProduct.valueOf(s);
            return AdAdminService.TYPES.contains(p) ? p : null;
        });
        String advertiserId = BizFields.strip(advertiserText);
        AdAdvertiserService.Ref ref = null;
        if (advertiserId != null) {
            ref = AdAdvertiserService.Ref.parse(advertiserId);
            if (ref == null) {
                f.add("advertiserId", "bizapp.value.invalid");
            }
        }
        return new Query(from, to, type, ref, advertiserId);
    }

    /** 거른 광고주 — 없는 광고주는 null(404 아님) */
    private AdStatsAdminView.Advertiser filteredAdvertiser(Query q) {
        if (q.advertiser() == null) {
            return null;
        }
        if (q.advertiser().advertiserId() != null) {
            return advertisers.find(q.advertiser().advertiserId()).map(a -> AdStatsAdminView.Advertiser.of(AdAdvertiserService.option(a))).orElse(null);
        }
        return advertisers.business(q.advertiser().businessProfileId())
                .map(b -> new AdStatsAdminView.Advertiser("biz-" + b.profileId(), b.companyName(), "BUSINESS")).orElse(null);
    }

    /** 광고주별 합 — 일정표 광고주 ID 로 묶고(ID 없는 이름만 광고주는 뺀다) 노출 차례 상위 10 */
    private List<AdStatsAdminView.AdvertiserRow> topAdvertisers(Map<Long, long[]> perApp) {
        if (perApp.isEmpty()) {
            return List.of();
        }
        List<AppRow> list = apps.findAll(perApp.keySet());
        Map<Long, AdvertiserRow> advs = advertiserRows(list);
        Map<String, AdStatsAdminView.Advertiser> who = new LinkedHashMap<>();
        Map<String, long[]> sums = new LinkedHashMap<>();
        for (AppRow a : list) {
            AdAdvertiserService.Option o = AdAdminService.advertiserOf(a, a.advertiserId() == null ? null : advs.get(a.advertiserId()));
            if (o.id() == null) {
                continue;
            }
            long[] c = perApp.get(a.id());
            who.putIfAbsent(o.id(), AdStatsAdminView.Advertiser.of(o));
            long[] s = sums.computeIfAbsent(o.id(), k -> new long[3]);
            add(s, c[0], c[1]);
            s[2] += c[0] > 0 ? 1 : 0;
        }
        return sums.entrySet().stream().filter(e -> nonZero(e.getValue()))
                .map(e -> new AdStatsAdminView.AdvertiserRow(who.get(e.getKey()), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingLong(AdStatsAdminView.AdvertiserRow::impressions).reversed()
                        .thenComparing(Comparator.comparingLong(AdStatsAdminView.AdvertiserRow::clicks).reversed())
                        .thenComparing(r -> r.advertiser().id()))
                .limit(TOP_ADVERTISERS).toList();
    }

    private Map<Long, AdvertiserRow> advertiserRows(java.util.Collection<AppRow> list) {
        Set<Long> ids = new HashSet<>();
        list.stream().map(AppRow::advertiserId).filter(Objects::nonNull).forEach(ids::add);
        return ids.isEmpty() ? Collections.emptyMap() : advertisers.findAll(ids);
    }

    private static int number(BizFields f, String field, String text, int fallback, int min, int max) {
        String s = BizFields.strip(text);
        if (s == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(s);
            if (n >= min && n <= max) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 오류
        }
        f.add(field, "bizapp.value.invalid");
        return fallback;
    }

    private static void add(long[] into, long impressions, long clicks) {
        into[0] += impressions;
        into[1] += clicks;
    }

    private static boolean nonZero(long[] c) {
        return c[0] > 0 || c[1] > 0;
    }
}
