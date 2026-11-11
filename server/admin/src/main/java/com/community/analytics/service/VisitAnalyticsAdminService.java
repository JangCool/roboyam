package com.community.analytics.service;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.dto.VisitAnalyticsAdmin.Collect;
import com.community.analytics.dto.VisitAnalyticsAdmin.FlowEnd;
import com.community.analytics.dto.VisitAnalyticsAdmin.GroupCount;
import com.community.analytics.dto.VisitAnalyticsAdmin.PageRow;
import com.community.analytics.dto.VisitAnalyticsAdmin.Range;
import com.community.analytics.repository.master.VisitAnalyticsStore;
import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.analytics.store.VisitPeriods;
import com.community.dashboard.repository.master.DashboardAnalyticsStore.Name;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 「방문자 통계」(site-analytics-contract 「「방문자 통계」 화면 (화면 확정 2026-11-04)」·「2단계」) — 관리자 API 7개 + 2단계 환경·검색어·봇 3개의 계산(수집 진단·정보주체 요청은
 * {@link VisitDiagnosticsAdminService}). 모두 site.read(컨트롤러), 응답 모양은 {@link VisitAnalyticsAdmin}.
 *
 * <ul>
 *   <li><b>기간</b>: 한국 날짜 {@code from}·{@code to}(양끝 포함). 비었거나 형식이 틀리거나 {@code from > to} 이거나 {@value #MAX_DAYS}일을 넘으면 400 {@code fields.from},
 *       {@code to} 가 오늘보다 뒤면 400 {@code fields.to}.</li>
 *   <li><b>원천</b>: 요약·추이·요일·시간대 = {@code analytics_visit_hourly} · 지금 접속 = {@code analytics_visit_active} · 페이지·서랍 일별 = {@code analytics_page_daily} ·
 *       유입 = {@code analytics_referrer_daily} · 캠페인 = {@code analytics_campaign_daily}(analytics V3) · 이동·서랍 들어온/나간 곳 = {@code analytics_flow_daily} — 모두 집계 표.
 *       원본({@code analytics_page_views})은 실시간의 최근 30분만. 2단계: 환경 = {@code analytics_env_daily} · 검색어 = {@code analytics_search_daily}(+ 걸러진 수
 *       {@code analytics_search_filtered_daily}) · 봇 = {@code analytics_bot_daily}(사람 대비 = {@code analytics_visit_hourly} 페이지뷰, 방문자·페이지뷰 수집이 꺼지면 null) — analytics V4.</li>
 *   <li><b>수집 스위치</b>: 응답의 {@code collect} = 「전체 && 항목」(설정 30초 사본). <b>꺼진 항목의 원천은 읽지 않는다</b>(그 칸은 0·빈 배열).
 *       페이지 순위·서랍 일별은 3단계부터 「방문자·페이지뷰」({@code visits} — {@link StatsCollection#pagesOn})에 딸려 그 스위치를 따른다(원본 기록과 무관).
 *       캠페인은 유입 경로({@code referrers}) 스위치를 따른다. 주소는 라우트 기준 묶음({@code /posts/{id}} · {@code /{unknown}})이고 묶음 줄은 이름이 null 이다.</li>
 *   <li><b>오늘</b>: 오늘이 낀 기간의 직전 기간은 「같은 시각이 든 시간 칸까지」(대시보드 방문 현황과 같은 칸 단위). 요일·시간대는 오늘의 지금 시간(집계 중)과 그 뒤 칸을 빼고
 *       {@code days} 도 세지 않는다(admin-web {@code UtilVisitTime} 과 같은 규칙).</li>
 *   <li><b>이름</b>: 돌려줄 줄의 경로만 {@link VisitPageTitles} 로(표마다 PK 한 번).</li>
 *   <li><b>캐시</b>: 결과를 서버 메모리에 {@link #TTL}(실시간은 {@link #REALTIME_TTL}, 운영자 모두 같은 키 하나) — 키 = API + 정규화한 질의 값 + 언어 + 수집 스위치
 *       (설정을 바꾸면 사본이 바뀌는 대로 새 키라 옛 값이 남지 않는다). 검증은 캐시 앞에서 한다(틀린 입력은 캐시에 들어가지 않는다).</li>
 * </ul>
 */
@Service
public class VisitAnalyticsAdminService {

    static final int MAX_DAYS = 90;
    static final Duration TTL = Duration.ofSeconds(60);
    static final Duration REALTIME_TTL = Duration.ofSeconds(20);
    static final Duration ACTIVE_WINDOW = Duration.ofMinutes(5);
    static final int REALTIME_MINUTES = 30;
    static final Duration REALTIME_RECENT = Duration.ofMinutes(5);

    static final int PAGE_DEFAULT_SIZE = 20;
    static final int PAGE_MAX_SIZE = 100;
    static final int PAGE_MAX_INDEX = 10_000;
    static final int Q_MAX = 100;
    static final int PATH_MAX = 300;
    static final int FLOW_ENDS = 5;
    static final int TOP_MOVES = 10;
    static final int DOMAINS = 10;
    static final int CAMPAIGNS = 20;
    static final int REALTIME_PAGES = 10;

    /** 화면 종류 — 수집기(VisitCollector.KINDS)와 같은 값 */
    static final Set<String> KINDS = Set.of("HOME", "BOARD", "POST", "GROUP_BUY", "HOTDEAL", "GROUP", "POINT_SHOP", "SEARCH", "MY", "OTHER");
    /** 유입 분류 — 화면 차례(같은 수일 때의 차례) */
    static final List<String> GROUPS = List.of("SEARCH", "SOCIAL", "DIRECT", "OTHER_SITE", "CAMPAIGN");
    /** 2단계 분류값 — 화면 차례(admin-web {@code VISIT_DEVICES}·{@code VISIT_BROWSERS}·{@code VISIT_OSES}·{@code VISIT_SCREENS}·{@code VISIT_BOT_KINDS}, 수집기와 같은 값) */
    static final List<String> DEVICES = List.of("MOBILE", "TABLET", "DESKTOP");
    static final List<String> BROWSERS = List.of("CHROME", "SAFARI", "EDGE", "SAMSUNG", "WHALE", "FIREFOX", "OTHER");
    static final List<String> OSES = List.of("WINDOWS", "MACOS", "ANDROID", "IOS", "LINUX", "OTHER");
    static final List<String> SCREENS = List.of("W480", "W768", "W1024", "W1440", "WIDE");
    static final List<String> BOT_KINDS = List.of("GOOGLE", "NAVER", "BING", "DAUM", "OTHER_CRAWLER", "TOOL");
    static final int SEARCH_TERMS = 20;
    /** 월별 보기 — 한 번에 볼 수 있는 달 수 · 많이 본 페이지 수 */
    static final int MAX_MONTHS = 36;
    static final int MONTHLY_PAGES = 10;
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]");
    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");

    private final VisitAnalyticsStore store;
    private final VisitPageTitles titles;
    private final StatsCollectionSettings settings;
    private final Messages messages;
    private final Clock clock;
    private final VisitResultCache cache;
    private final VisitUniqueCounts uniqueCounts;

    /** 시계는 {@code Clock} 빈이 하나면 그것(없으면 UTC 시스템 시계) — 다른 서비스와 같은 주입 방식 */
    @Autowired
    public VisitAnalyticsAdminService(VisitAnalyticsStore store, VisitPageTitles titles, StatsCollectionSettings settings, Messages messages,
                                      ObjectProvider<Clock> clocks) {
        this(store, titles, settings, messages, clocks.getIfUnique(Clock::systemUTC));
    }

    /** 시험용 — 시계를 직접 준다 */
    VisitAnalyticsAdminService(VisitAnalyticsStore store, VisitPageTitles titles, StatsCollectionSettings settings, Messages messages, Clock clock) {
        this.store = store;
        this.titles = titles;
        this.settings = settings;
        this.messages = messages;
        this.clock = clock;
        this.cache = new VisitResultCache(clock);
        this.uniqueCounts = new VisitUniqueCounts(store);
    }

    /** 결과 캐시(시험·점검용) */
    VisitResultCache cache() {
        return cache;
    }

    // ── 요약 ─────────────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.Summary summary(String fromText, String toText) {
        Period p = period(fromText, toText);
        StatsCollection s = settings.current();
        return cache.get(key("summary", s, p.from, p.to), TTL, at -> computeSummary(p, s, at));
    }

    private VisitAnalyticsAdmin.Summary computeSummary(Period p, StatsCollection s, Instant at) {
        int n = p.days();
        LocalDate today = ServiceTime.dateOf(at);
        Instant nowHour = at.truncatedTo(ChronoUnit.HOURS);
        LocalDate prevFrom = p.from.minusDays(n);
        LocalDate prevTo = p.to.minusDays(n);
        Range prevRange = new Range(prevFrom.toString(), prevTo.toString());
        boolean single = n == 1;
        String granularity = single ? "HOUR" : "DAY";
        Collect collect = collect(s);
        if (!s.visitsOn()) {
            return new VisitAnalyticsAdmin.Summary(p.range(), asOf(at), granularity, new VisitAnalyticsAdmin.Counts(0, 0, 0),
                    new VisitAnalyticsAdmin.PreviousCounts(prevRange, 0, 0, 0), List.of(), collect, s.rawRetentionMonths(), 0);
        }
        boolean includesToday = !p.to.isBefore(today);
        Instant start = startOf(p.from);
        // 이번 기간 끝: 오늘이 끼면 지금 시각이 든 칸까지
        Instant end = includesToday ? nowHour.plus(Duration.ofHours(1)) : startOf(p.to.plusDays(1));
        // 직전 기간 끝: 오늘이 끼면 같은 시각이 든 칸까지(n 일 앞) — 아니면 이번 기간 시작
        Instant prevEnd = includesToday ? end.minus(Duration.ofDays(n)) : start;
        long visitors = 0;
        long newVisitors = 0;
        long views = 0;
        long pv = 0;
        long pnv = 0;
        long pviews = 0;
        Map<Instant, VisitAnalyticsStore.Hour> byHour = new HashMap<>();
        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (VisitAnalyticsStore.Hour h : store.hours(startOf(prevFrom), end)) {
            if (h.hour().isBefore(start)) {
                if (h.hour().isBefore(prevEnd)) {
                    pv += h.visitors();
                    pnv += h.newVisitors();
                    pviews += h.pageViews();
                }
                continue;
            }
            visitors += h.visitors();
            newVisitors += h.newVisitors();
            views += h.pageViews();
            if (single) {
                byHour.put(h.hour(), h);
            } else {
                long[] d = byDay.computeIfAbsent(ServiceTime.dateOf(h.hour()), k -> new long[3]);
                d[0] += h.visitors();
                d[1] += h.newVisitors();
                d[2] += h.pageViews();
            }
        }
        List<VisitAnalyticsAdmin.SeriesPoint> series = new ArrayList<>();
        if (single) {
            for (Instant h = start; h.isBefore(end); h = h.plus(Duration.ofHours(1))) {
                VisitAnalyticsStore.Hour row = byHour.get(h);
                series.add(new VisitAnalyticsAdmin.SeriesPoint(h.toString(), row == null ? 0 : row.visitors(), row == null ? 0 : row.newVisitors(),
                        row == null ? 0 : row.pageViews()));
            }
        } else {
            for (LocalDate d = p.from; !d.isAfter(p.to); d = d.plusDays(1)) {
                long[] row = byDay.getOrDefault(d, new long[3]);
                series.add(new VisitAnalyticsAdmin.SeriesPoint(d.toString(), row[0], row[1], row[2]));
            }
        }
        long active = store.activeSince(at.minus(ACTIVE_WINDOW));
        return new VisitAnalyticsAdmin.Summary(p.range(), asOf(at), granularity, new VisitAnalyticsAdmin.Counts(visitors, newVisitors, views),
                new VisitAnalyticsAdmin.PreviousCounts(prevRange, pv, pnv, pviews), series, collect, s.rawRetentionMonths(), active);
    }

    // ── 페이지 ───────────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.PagePage pages(String fromText, String toText, String kindText, String qText, String pageText, String sizeText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Period p = periodInto(fromText, toText, fields);
        String kind = blank(kindText) ? null : kindText.strip();
        if (kind != null && !KINDS.contains(kind)) {
            fields.put("kind", new MessageRef("analytics.visits.kind.invalid"));
        }
        String q = blank(qText) ? null : qText.strip();
        if (q != null && q.length() > Q_MAX) {
            fields.put("q", new MessageRef("analytics.visits.q.tooLong", Q_MAX));
        }
        MessageRef pageRange = new MessageRef("analytics.visits.page.range", PAGE_MAX_SIZE);
        Integer page = integer(pageText, 0, 0, PAGE_MAX_INDEX);
        if (page == null) {
            fields.put("page", pageRange);
        }
        Integer size = integer(sizeText, PAGE_DEFAULT_SIZE, 1, PAGE_MAX_SIZE);
        if (size == null) {
            fields.put("size", pageRange);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        StatsCollection s = settings.current();
        return cache.get(key("pages", s, p.from, p.to, kind, q, page, size), TTL, at -> computePages(p, s, kind, q, page, size));
    }

    private VisitAnalyticsAdmin.PagePage computePages(Period p, StatsCollection s, String kind, String q, int page, int size) {
        if (!s.pagesOn()) {
            return new VisitAnalyticsAdmin.PagePage(List.of(), 0, 0);
        }
        List<String> boards = q == null ? List.of() : titles.boardPathsMatching(q);
        List<VisitAnalyticsStore.PageRank> rows = store.pages(p.from, p.to, kind, q, boards, page * size, size);
        long total;
        long totalViews;
        if (rows.isEmpty()) {
            if (page == 0) {
                return new VisitAnalyticsAdmin.PagePage(List.of(), 0, 0);
            }
            long[] t = store.pageTotals(p.from, p.to, kind, q, boards);
            total = t[0];
            totalViews = t[1];
        } else {
            total = rows.get(0).total();
            totalViews = rows.get(0).totalViews();
        }
        Map<String, Name> names = titles.resolve(rows.stream().map(VisitAnalyticsStore.PageRank::path).toList());
        List<PageRow> items = rows.stream().map(r -> pageRow(r.path(), r.kind(), r.views(), names)).toList();
        return new VisitAnalyticsAdmin.PagePage(items, total, totalViews);
    }

    // ── 페이지 서랍 ──────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.PageDetail pageDetail(String fromText, String toText, String pathText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Period p = periodInto(fromText, toText, fields);
        String path = path(pathText);
        if (path == null) {
            fields.put("path", new MessageRef("analytics.visits.path.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        StatsCollection s = settings.current();
        return cache.get(key("pageDetail", s, p.from, p.to, path), TTL, at -> computePageDetail(p, s, path));
    }

    private VisitAnalyticsAdmin.PageDetail computePageDetail(Period p, StatsCollection s, String path) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        String kind = null;
        if (s.pagesOn()) {
            store.pageDays(p.from, p.to, path).forEach(d -> byDay.put(d.day(), d.count()));
            kind = byDay.isEmpty() ? null : store.pageKind(p.from, p.to, path);
        }
        List<VisitAnalyticsAdmin.DayViews> days = new ArrayList<>(p.days());
        long views = 0;
        for (LocalDate d = p.from; !d.isAfter(p.to); d = d.plusDays(1)) {
            long n = byDay.getOrDefault(d, 0L);
            views += n;
            days.add(new VisitAnalyticsAdmin.DayViews(d.toString(), n));
        }
        Ends ends = ends(p, s, path);
        Map<String, Name> names = titles.resolve(ends.paths(path));
        Name name = names.get(path);
        return new VisitAnalyticsAdmin.PageDetail(path, kind == null ? "OTHER" : kind, name == null ? null : name.name(), name == null ? null : name.nameEn(), views, days,
                ends.incoming(names), ends.incomingTotal(), ends.outgoing(names), ends.outgoingTotal());
    }

    // ── 유입 ─────────────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.Referrers referrers(String fromText, String toText, String detailText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Period p = periodInto(fromText, toText, fields);
        Boolean detail = blank(detailText) ? Boolean.TRUE : bool(detailText);
        if (detail == null) {
            fields.put("detail", new MessageRef("analytics.visits.detail.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        StatsCollection s = settings.current();
        boolean withDetail = detail;
        return cache.get(key("referrers", s, p.from, p.to, withDetail), TTL, at -> computeReferrers(p, s, withDetail));
    }

    private VisitAnalyticsAdmin.Referrers computeReferrers(Period p, StatsCollection s, boolean detail) {
        if (!s.referrersOn()) {
            return new VisitAnalyticsAdmin.Referrers(0, List.of(), List.of(), List.of(), List.of());
        }
        Map<String, Long> byGroup = new HashMap<>();
        List<VisitAnalyticsAdmin.ReferrerSeriesPoint> series = new ArrayList<>();
        List<VisitAnalyticsAdmin.CampaignRow> campaigns = List.of();
        if (detail) {
            Map<LocalDate, Map<String, Long>> byDay = new HashMap<>();
            for (VisitAnalyticsStore.DayGroup g : store.referrerDayGroups(p.from, p.to)) {
                byDay.computeIfAbsent(g.day(), k -> new HashMap<>()).merge(g.group(), g.visits(), Long::sum);
                byGroup.merge(g.group(), g.visits(), Long::sum);
            }
            for (LocalDate d = p.from; !d.isAfter(p.to); d = d.plusDays(1)) {
                Map<String, Long> day = byDay.getOrDefault(d, Map.of());
                Map<String, Long> groups = new LinkedHashMap<>();
                GROUPS.forEach(g -> groups.put(g, day.getOrDefault(g, 0L)));
                series.add(new VisitAnalyticsAdmin.ReferrerSeriesPoint(d.toString(), groups));
            }
            campaigns = store.campaigns(p.from, p.to, CAMPAIGNS).stream()
                    .map(c -> new VisitAnalyticsAdmin.CampaignRow(emptyToNull(c.source()), emptyToNull(c.medium()), emptyToNull(c.campaign()), c.visits())).toList();
        } else {
            store.referrerGroups(p.from, p.to).forEach(c -> byGroup.merge(c.key(), c.count(), Long::sum));
        }
        List<GroupCount> groups = groups(byGroup);
        long total = groups.stream().mapToLong(GroupCount::visits).sum();
        List<VisitAnalyticsAdmin.ReferrerDomain> domains = store.referrerDomains(p.from, p.to, DOMAINS).stream()
                .map(c -> new VisitAnalyticsAdmin.ReferrerDomain(c.key(), c.count())).toList();
        return new VisitAnalyticsAdmin.Referrers(total, groups, domains, campaigns, series);
    }

    // ── 이동 흐름 ────────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.Flows flows(String fromText, String toText, String pathText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Period p = periodInto(fromText, toText, fields);
        String path = null;
        if (!blank(pathText)) {
            path = path(pathText);
            if (path == null) {
                fields.put("path", new MessageRef("analytics.visits.path.invalid"));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        StatsCollection s = settings.current();
        String given = path;
        return cache.get(key("flows", s, p.from, p.to, given), TTL, at -> computeFlows(p, s, given));
    }

    private VisitAnalyticsAdmin.Flows computeFlows(Period p, StatsCollection s, String given) {
        String path = given;
        if (path == null && s.flowsOn()) {
            path = store.topDestination(p.from, p.to);
        }
        if (path == null) {
            path = "/";
        }
        String kind = s.pagesOn() ? store.pageKind(p.from, p.to, path) : null;
        Ends ends = ends(p, s, path);
        List<VisitAnalyticsStore.Move> moves = s.flowsOn() ? store.topMoves(p.from, p.to, TOP_MOVES) : List.of();
        Map<String, Name> names = titles.resolve(ends.paths(path));
        Name name = names.get(path);
        List<VisitAnalyticsAdmin.FlowMove> top = moves.stream().map(m -> new VisitAnalyticsAdmin.FlowMove(m.from(), m.to(), m.moves())).toList();
        return new VisitAnalyticsAdmin.Flows(path, kind == null ? "OTHER" : kind, name == null ? null : name.name(), name == null ? null : name.nameEn(),
                ends.incoming(names), ends.incomingTotal(), ends.outgoing(names), ends.outgoingTotal(), top);
    }

    /** 한 페이지의 들어온 곳·나간 곳 상위 5 + 합 — 이동 수집이 꺼져 있으면 빈 값(표를 읽지 않는다) */
    private Ends ends(Period p, StatsCollection s, String path) {
        if (!s.flowsOn()) {
            return new Ends(List.of(), List.of());
        }
        return new Ends(store.incoming(p.from, p.to, path, FLOW_ENDS), store.outgoing(p.from, p.to, path, FLOW_ENDS));
    }

    private record Ends(List<VisitAnalyticsStore.End> in, List<VisitAnalyticsStore.End> out) {

        Collection<String> paths(String self) {
            Set<String> all = new LinkedHashSet<>();
            all.add(self);
            in.forEach(e -> all.add(e.path()));
            out.forEach(e -> all.add(e.path()));
            return all;
        }

        long incomingTotal() {
            return in.isEmpty() ? 0 : in.get(0).total();
        }

        long outgoingTotal() {
            return out.isEmpty() ? 0 : out.get(0).total();
        }

        /** 「바로 들어옴」(저장은 from_path '') → path null */
        List<FlowEnd> incoming(Map<String, Name> names) {
            return in.stream().map(e -> end(e, names)).toList();
        }

        List<FlowEnd> outgoing(Map<String, Name> names) {
            return out.stream().map(e -> end(e, names)).toList();
        }

        private static FlowEnd end(VisitAnalyticsStore.End e, Map<String, Name> names) {
            if (e.path() == null || e.path().isEmpty()) {
                return new FlowEnd(null, null, null, e.moves());
            }
            Name name = names.get(e.path());
            return new FlowEnd(e.path(), name == null ? null : name.name(), name == null ? null : name.nameEn(), e.moves());
        }
    }

    // ── 요일·시간대 ──────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.Time time(String fromText, String toText) {
        Period p = period(fromText, toText);
        StatsCollection s = settings.current();
        return cache.get(key("time", s, p.from, p.to), TTL, at -> computeTime(p, s, at));
    }

    private VisitAnalyticsAdmin.Time computeTime(Period p, StatsCollection s, Instant at) {
        long[][] visitors = new long[7][24];
        long[][] views = new long[7][24];
        int[][] days = new int[7][24];
        if (s.visitsOn()) {
            LocalDate today = ServiceTime.dateOf(at);
            Instant nowHour = at.truncatedTo(ChronoUnit.HOURS);
            Instant end = startOf(p.to.plusDays(1));
            if (end.isAfter(nowHour)) {
                end = nowHour; // 오늘의 지금 시간(집계 중)과 그 뒤는 넣지 않는다
            }
            for (VisitAnalyticsStore.Hour h : store.hours(startOf(p.from), end)) {
                var local = h.hour().atZone(ServiceTime.ZONE);
                int w = local.getDayOfWeek().getValue() - 1;
                visitors[w][local.getHour()] += h.visitors();
                views[w][local.getHour()] += h.pageViews();
            }
            int nowLocalHour = at.atZone(ServiceTime.ZONE).getHour();
            for (LocalDate d = p.from; !d.isAfter(p.to); d = d.plusDays(1)) {
                int until = d.isBefore(today) ? 23 : d.isEqual(today) ? nowLocalHour - 1 : -1;
                int w = d.getDayOfWeek().getValue() - 1;
                for (int hour = 0; hour <= until; hour++) {
                    days[w][hour]++;
                }
            }
        }
        List<VisitAnalyticsAdmin.TimeCell> cells = new ArrayList<>(168);
        for (int w = 0; w < 7; w++) {
            for (int hour = 0; hour < 24; hour++) {
                cells.add(new VisitAnalyticsAdmin.TimeCell(w + 1, hour, visitors[w][hour], views[w][hour], days[w][hour]));
            }
        }
        return new VisitAnalyticsAdmin.Time(p.range(), asOf(at), cells);
    }

    // ── 실시간 ───────────────────────────────────────────────────────────────

    /** 기간 없음 — 운영자 모두 같은 키 하나(언어도 넣지 않는다 — 응답에 요청 언어 문구가 없다), 수명 {@link #REALTIME_TTL} */
    public VisitAnalyticsAdmin.Realtime realtime() {
        StatsCollection s = settings.current();
        return cache.get("realtime|" + fingerprint(s), REALTIME_TTL, at -> computeRealtime(s, at));
    }

    private VisitAnalyticsAdmin.Realtime computeRealtime(StatsCollection s, Instant at) {
        long active = s.visitsOn() ? store.activeSince(at.minus(ACTIVE_WINDOW)) : 0;
        if (!s.rawEventsOn()) {
            return new VisitAnalyticsAdmin.Realtime(asOf(at), collect(s), active, List.of(), List.of(), List.of());
        }
        Instant nowMinute = at.truncatedTo(ChronoUnit.MINUTES);
        Instant since = nowMinute.minus(Duration.ofMinutes(REALTIME_MINUTES - 1));
        Map<Instant, Long> byMinute = new HashMap<>();
        store.perMinute(since).forEach(m -> byMinute.put(m.at(), m.pageViews()));
        List<VisitAnalyticsAdmin.MinutePoint> perMinute = new ArrayList<>(REALTIME_MINUTES);
        for (Instant m = since; !m.isAfter(nowMinute); m = m.plus(Duration.ofMinutes(1))) {
            perMinute.add(new VisitAnalyticsAdmin.MinutePoint(m, byMinute.getOrDefault(m, 0L)));
        }
        Instant recent = at.minus(REALTIME_RECENT);
        List<VisitAnalyticsStore.Page> pages = store.recentPages(recent, REALTIME_PAGES);
        Map<String, Name> names = titles.resolve(pages.stream().map(VisitAnalyticsStore.Page::path).toList());
        List<PageRow> rows = pages.stream().map(r -> pageRow(r.path(), r.kind(), r.views(), names)).toList();
        Map<String, Long> byGroup = new HashMap<>();
        store.recentReferrerGroups(recent).forEach(c -> byGroup.merge(c.key(), c.count(), Long::sum));
        return new VisitAnalyticsAdmin.Realtime(asOf(at), collect(s), active, perMinute, rows, groups(byGroup));
    }

    // ── 2단계 — 환경 ─────────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.Environment environment(String fromText, String toText) {
        Period p = period(fromText, toText);
        StatsCollection s = settings.current();
        return cache.get(key("environment", s, p.from, p.to), TTL, at -> computeEnvironment(p, s, at));
    }

    private VisitAnalyticsAdmin.Environment computeEnvironment(Period p, StatsCollection s, Instant at) {
        Map<String, Long> devices = new HashMap<>();
        Map<String, Long> browsers = new HashMap<>();
        Map<String, Long> oses = new HashMap<>();
        Map<String, Long> screens = new HashMap<>();
        Map<LocalDate, Map<String, Long>> byDay = new HashMap<>();
        if (s.environmentOn()) {
            for (VisitAnalyticsStore.EnvCount c : store.envCounts(p.from, p.to)) {
                switch (c.dim()) {
                    case "D" -> {
                        devices.merge(c.key(), c.visits(), Long::sum);
                        byDay.computeIfAbsent(c.day(), k -> new HashMap<>()).merge(c.key(), c.visits(), Long::sum);
                    }
                    case "B" -> browsers.merge(c.key(), c.visits(), Long::sum);
                    case "O" -> oses.merge(c.key(), c.visits(), Long::sum);
                    default -> screens.merge(c.key(), c.visits(), Long::sum);
                }
            }
        }
        long total = devices.values().stream().mapToLong(Long::longValue).sum();
        List<VisitAnalyticsAdmin.DeviceSeriesPoint> series = new ArrayList<>();
        if (s.environmentOn()) {
            for (LocalDate d = p.from; !d.isAfter(p.to); d = d.plusDays(1)) {
                series.add(new VisitAnalyticsAdmin.DeviceSeriesPoint(d.toString(), filled(DEVICES, byDay.getOrDefault(d, Map.of()))));
            }
        }
        return new VisitAnalyticsAdmin.Environment(p.range(), asOf(at), collectMore(s), total, shares(DEVICES, devices, true), shares(BROWSERS, browsers, true),
                shares(OSES, oses, true), shares(SCREENS, screens, false), series);
    }

    // ── 2단계 — 검색어 ───────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.Search search(String fromText, String toText) {
        Period p = period(fromText, toText);
        StatsCollection s = settings.current();
        return cache.get(key("search", s, p.from, p.to), TTL, at -> computeSearch(p, s, at));
    }

    private VisitAnalyticsAdmin.Search computeSearch(Period p, StatsCollection s, Instant at) {
        if (!s.searchTermsOn()) {
            return new VisitAnalyticsAdmin.Search(p.range(), asOf(at), collectMore(s), new VisitAnalyticsAdmin.SearchTotals(0, 0, 0, 0), List.of(), List.of());
        }
        VisitAnalyticsStore.SearchTotals t = store.searchTotals(p.from, p.to);
        List<VisitAnalyticsAdmin.SearchTerm> terms = store.topTerms(p.from, p.to, SEARCH_TERMS).stream()
                .map(r -> new VisitAnalyticsAdmin.SearchTerm(r.term(), r.searches(), r.noResult())).toList();
        List<VisitAnalyticsAdmin.NoResultTerm> misses = store.noResultTerms(p.from, p.to, SEARCH_TERMS).stream()
                .map(r -> new VisitAnalyticsAdmin.NoResultTerm(r.term(), r.noResult(), r.lastNoResultDay().toString())).toList();
        return new VisitAnalyticsAdmin.Search(p.range(), asOf(at), collectMore(s), new VisitAnalyticsAdmin.SearchTotals(t.searches(), t.noResult(), t.terms(), t.filtered()),
                terms, misses);
    }

    // ── 2단계 — 봇·크롤러 ────────────────────────────────────────────────────

    public VisitAnalyticsAdmin.Bots bots(String fromText, String toText) {
        Period p = period(fromText, toText);
        StatsCollection s = settings.current();
        return cache.get(key("bots", s, p.from, p.to), TTL, at -> computeBots(p, s, at));
    }

    private VisitAnalyticsAdmin.Bots computeBots(Period p, StatsCollection s, Instant at) {
        LocalDate today = ServiceTime.dateOf(at);
        Map<String, Long> kinds = new HashMap<>();
        Map<LocalDate, Map<String, Long>> byDay = new HashMap<>();
        long todayBots = 0;
        if (s.botsOn()) {
            for (VisitAnalyticsStore.BotDay d : store.botDays(p.from, p.to, today)) {
                if (d.day().isEqual(today)) {
                    todayBots += d.requests();
                }
                if (!d.day().isBefore(p.from) && !d.day().isAfter(p.to)) {
                    kinds.merge(d.kind(), d.requests(), Long::sum);
                    byDay.computeIfAbsent(d.day(), k -> new HashMap<>()).merge(d.kind(), d.requests(), Long::sum);
                }
            }
        }
        long totalBots = kinds.values().stream().mapToLong(Long::longValue).sum();
        Long humansToday = s.visitsOn() ? store.pageViews(startOf(today), startOf(today.plusDays(1))) : null;
        Long humansRange = s.visitsOn() ? store.pageViews(startOf(p.from), startOf(p.to.plusDays(1))) : null;
        List<VisitAnalyticsAdmin.BotKindCount> kindRows = new ArrayList<>(BOT_KINDS.size());
        BOT_KINDS.forEach(k -> kindRows.add(new VisitAnalyticsAdmin.BotKindCount(k, kinds.getOrDefault(k, 0L))));
        kindRows.sort(Comparator.comparingLong(VisitAnalyticsAdmin.BotKindCount::requests).reversed());
        List<VisitAnalyticsAdmin.BotSeriesPoint> series = new ArrayList<>();
        if (s.botsOn()) {
            for (LocalDate d = p.from; !d.isAfter(p.to); d = d.plusDays(1)) {
                series.add(new VisitAnalyticsAdmin.BotSeriesPoint(d.toString(), filled(BOT_KINDS, byDay.getOrDefault(d, Map.of()))));
            }
        }
        return new VisitAnalyticsAdmin.Bots(p.range(), asOf(at), collectMore(s), new VisitAnalyticsAdmin.BotCounts(todayBots, humansToday),
                new VisitAnalyticsAdmin.BotCounts(totalBots, humansRange), kindRows, series);
    }

    // ── 3단계 — 주·월 순방문자 ───────────────────────────────────────────────

    /**
     * 기간 없음(오늘 기준) — 방문자·회원의 이번/지난 주·달, 같은 시점까지, 최근 12주·12개월({@link VisitUniqueCounts}). 수집({@code visits})이 꺼져 있으면 숫자 표를 읽지 않고
     * 모든 수가 null(화면은 「수집이 꺼져 있어요」). 키 = 오늘 날짜 + 수집 스위치 + 언어.
     */
    public VisitAnalyticsAdmin.Uniques uniques() {
        StatsCollection s = settings.current();
        LocalDate today = ServiceTime.today(clock);
        return cache.get(key("uniques", s, today), TTL,
                at -> new VisitAnalyticsAdmin.Uniques(asOf(at), collect(s), uniqueCounts.audiences(ServiceTime.dateOf(at), s.visitsOn())));
    }

    // ── 3단계 — 월별 보기 ────────────────────────────────────────────────────

    /**
     * 달 {@code from}·{@code to}({@code YYYY-MM}, 양끝 포함, 최대 {@value #MAX_MONTHS}개월). 비었거나·형식·{@code from > to}·개월 초과 = 400 {@code fields.from},
     * 이번 달 뒤 = 400 {@code fields.to}. 수집 스위치가 꺼져 있어도 쌓인 숫자를 준다(사용자 결정 ⓐ — {@code collect} 는 그대로, 화면이 안내만).
     * 합친 달(analytics_monthly_rollups 에 있고 이번 달·1~2일의 지난달이 아닌 달)은 월별 표, 나머지 달은 일별·시간별 표를 더해 채운다.
     */
    public VisitAnalyticsAdmin.Monthly monthly(String fromText, String toText) {
        YearMonth from = month(fromText);
        YearMonth to = month(toText);
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
            fields.put("from", new MessageRef("analytics.visits.months.invalid", MAX_MONTHS));
        } else if (to.isAfter(YearMonth.from(ServiceTime.today(clock)))) {
            fields.put("to", new MessageRef("analytics.visits.months.future"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        StatsCollection s = settings.current();
        LocalDate today = ServiceTime.today(clock);
        return cache.get(key("monthly", s, from, to, today), TTL, at -> computeMonthly(from, to, s, ServiceTime.dateOf(at), at));
    }

    private VisitAnalyticsAdmin.Monthly computeMonthly(YearMonth from, YearMonth to, StatsCollection s, LocalDate today, Instant at) {
        LocalDate first = from.atDay(1);
        LocalDate last = to.atDay(1);
        LocalDate thisMonth = today.withDayOfMonth(1);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        Set<LocalDate> rolledRows = store.rolledMonths(first, last);
        List<LocalDate> rolled = new ArrayList<>();
        List<LocalDate> open = new ArrayList<>();
        for (LocalDate m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            // 이번 달 · 1~2일의 지난달(크론이 덮어써 다시 합치는 중) · 합친 기록이 없는 달은 일별·시간별에서
            boolean fresh = m.equals(thisMonth) || (m.equals(lastMonth) && today.getDayOfMonth() <= 2) || !rolledRows.contains(m);
            (fresh ? open : rolled).add(m);
        }
        Map<LocalDate, long[]> visits = new HashMap<>();
        store.visitMonths(rolled).forEach(v -> visits.put(v.month(), new long[] {v.visitorsDailySum(), v.newVisitors(), v.pageViews()}));
        for (LocalDate m : open) {
            visits.put(m, store.hourTotals(startOf(m), startOf(m.plusMonths(1))));
        }
        Map<String, LocalDate> firstDays = uniqueCounts.firstDays();
        Map<LocalDate, Long> visitorMau = uniqueCounts.months(VisitPeriods.VISITOR, first, last, firstDays);
        Map<LocalDate, Long> memberMau = uniqueCounts.months(VisitPeriods.MEMBER, first, last, firstDays);
        List<VisitAnalyticsAdmin.MonthlyPoint> months = new ArrayList<>();
        long sum = 0;
        long newSum = 0;
        long viewSum = 0;
        for (LocalDate m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            long[] v = visits.getOrDefault(m, new long[3]);
            sum += v[0];
            newSum += v[1];
            viewSum += v[2];
            months.add(new VisitAnalyticsAdmin.MonthlyPoint(YearMonth.from(m).toString(), v[0], v[1], v[2], visitorMau.get(m), memberMau.get(m), m.equals(thisMonth)));
        }
        List<VisitAnalyticsStore.PageRank> ranks = store.monthPages(rolled, open, MONTHLY_PAGES);
        Map<String, Name> names = titles.resolve(ranks.stream().map(VisitAnalyticsStore.PageRank::path).toList());
        List<PageRow> pages = ranks.stream().map(r -> pageRow(r.path(), r.kind(), r.views(), names)).toList();
        long pagesTotal = ranks.isEmpty() ? 0 : ranks.get(0).totalViews();
        Map<String, Long> byGroup = new HashMap<>();
        store.monthReferrerGroups(rolled, open).forEach(c -> byGroup.merge(c.key(), c.count(), Long::sum));
        List<GroupCount> groups = groups(byGroup);
        long referrerTotal = groups.stream().mapToLong(GroupCount::visits).sum();
        return new VisitAnalyticsAdmin.Monthly(new VisitAnalyticsAdmin.MonthRange(from.toString(), to.toString()), asOf(at), collect(s),
                new VisitAnalyticsAdmin.MonthlyTotals(sum, newSum, viewSum), months, pages, pagesTotal,
                new VisitAnalyticsAdmin.MonthlyReferrers(referrerTotal, referrerTotal == 0 ? List.of() : groups));
    }

    private static YearMonth month(String text) {
        if (blank(text)) {
            return null;
        }
        String v = text.strip();
        if (!MONTH.matcher(v).matches()) {
            return null;
        }
        try {
            return YearMonth.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 정해진 키 모두(없으면 0) — 키 차례 그대로 */
    private static Map<String, Long> filled(List<String> keys, Map<String, Long> counts) {
        Map<String, Long> out = new LinkedHashMap<>();
        keys.forEach(k -> out.put(k, counts.getOrDefault(k, 0L)));
        return out;
    }

    /** 정해진 키 모두(없으면 0, 모르는 키는 버린다) — {@code sorted} 면 많은 차례(같으면 키 차례), 아니면 키 차례 그대로(화면 너비 구간) */
    private static List<VisitAnalyticsAdmin.ShareRow> shares(List<String> keys, Map<String, Long> counts, boolean sorted) {
        List<VisitAnalyticsAdmin.ShareRow> out = new ArrayList<>(keys.size());
        keys.forEach(k -> out.add(new VisitAnalyticsAdmin.ShareRow(k, counts.getOrDefault(k, 0L))));
        if (sorted) {
            out.sort(Comparator.comparingLong(VisitAnalyticsAdmin.ShareRow::visits).reversed());
        }
        return out;
    }

    /** 2단계 수집 스위치(접은 값) — 수집 진단 서비스도 쓴다 */
    static VisitAnalyticsAdmin.CollectMore collectMore(StatsCollection s) {
        return new VisitAnalyticsAdmin.CollectMore(s.enabled(), s.environmentOn(), s.searchTermsOn(), s.botsOn(), s.visitsOn());
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    private record Period(LocalDate from, LocalDate to) {

        int days() {
            return (int) ChronoUnit.DAYS.between(from, to) + 1;
        }

        Range range() {
            return new Range(from.toString(), to.toString());
        }
    }

    private Period period(String fromText, String toText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Period p = periodInto(fromText, toText, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return p;
    }

    /** 기간 검사 — 틀리면 {@code fields} 에 넣고 null */
    private Period periodInto(String fromText, String toText, Map<String, MessageRef> fields) {
        LocalDate from = date(fromText);
        LocalDate to = date(toText);
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            fields.put("from", new MessageRef("analytics.visits.range.invalid", MAX_DAYS));
            return null;
        }
        if (to.isAfter(ServiceTime.today(clock))) {
            fields.put("to", new MessageRef("analytics.visits.range.future"));
            return null;
        }
        return new Period(from, to);
    }

    private static LocalDate date(String text) {
        if (blank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 수집기와 같은 경로 규칙 — 「/」로 시작(「//」 아님), 공백·제어 문자 없음, 300자 이하. 아니면 null */
    private static String path(String text) {
        if (text == null) {
            return null;
        }
        String v = text.strip();
        if (v.isEmpty() || v.length() > PATH_MAX || v.charAt(0) != '/' || v.startsWith("//") || CONTROL.matcher(v).find()) {
            return null;
        }
        return v;
    }

    private static Integer integer(String text, int fallback, int min, int max) {
        if (blank(text)) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(text.strip());
            return v < min || v > max ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(String text) {
        String v = text.strip();
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        return null;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    /** 다섯 분류 모두(없으면 0), 많은 차례 — 같으면 {@link #GROUPS} 차례(안정 정렬). 모르는 분류 이름은 버린다 */
    private static List<GroupCount> groups(Map<String, Long> byGroup) {
        List<GroupCount> out = new ArrayList<>(GROUPS.size());
        GROUPS.forEach(g -> out.add(new GroupCount(g, byGroup.getOrDefault(g, 0L))));
        out.sort(Comparator.comparingLong(GroupCount::visits).reversed());
        return out;
    }

    private static PageRow pageRow(String path, String kind, long views, Map<String, Name> names) {
        Name name = names.get(path);
        return new PageRow(path, kind, name == null ? null : name.name(), name == null ? null : name.nameEn(), views);
    }

    private static Collect collect(StatsCollection s) {
        return new Collect(s.enabled(), s.visitsOn(), s.rawEventsOn(), s.referrersOn(), s.flowsOn());
    }

    /** 결과에 영향을 주는 설정 값(동작 여부 일곱 칸 + 보관 개월 수) */
    static String fingerprint(StatsCollection s) {
        return (s.enabled() ? "E" : "e") + (s.visitsOn() ? "V" : "v") + (s.rawEventsOn() ? "R" : "r") + (s.referrersOn() ? "F" : "f") + (s.flowsOn() ? "L" : "l")
                + (s.environmentOn() ? "N" : "n") + (s.searchTermsOn() ? "S" : "s") + (s.botsOn() ? "B" : "b") + s.rawRetentionMonths();
    }

    /** 캐시 키 — API 이름 + 언어 + 수집 스위치 + 질의 값(구분자 \u0001, null 은 빈 칸) */
    private String key(String api, StatsCollection s, Object... parts) {
        StringBuilder sb = new StringBuilder(api).append('|').append(messages.locale().getLanguage()).append('|').append(fingerprint(s));
        for (Object part : parts) {
            sb.append('\u0001').append(part == null ? "" : part);
        }
        return sb.toString();
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    private static Instant asOf(Instant at) {
        return at.truncatedTo(ChronoUnit.SECONDS);
    }
}
