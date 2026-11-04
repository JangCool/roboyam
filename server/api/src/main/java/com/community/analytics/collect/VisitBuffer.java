package com.community.analytics.collect;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 방문 신호의 서버 메모리 모음 — 1분마다 {@link #drain()} 으로 통째로 넘기고 새로 시작한다({@code VisitFlusher}). 모든 메서드는 이 객체로 잠근다(신호 하나의 일은 맵 몇 번이라 짧다).
 * 2단계(site-analytics-contract 「2단계」)의 환경·검색어·봇 숫자와 수집 진단의 「무시한 신호」도 같은 모음에 든다(같은 1분 몰아쓰기).
 *
 * <p><b>메모리 상한</b>: 칸마다 줄 수 상한이 있고, 넘으면 <b>가장 오래 든 것부터 버리고</b> 1분에 한 번 경고 로그를 남긴다(방문 폭주·꾸민 신호로 메모리가 터지지 않게).
 * 버린 만큼 숫자가 덜 세진다 — 정상 방문량에서는 닿지 않는 값이다(분당 신호 기준). 버린 줄 수는 {@link Batch#dropped()} 로 넘겨 수집 진단에 남긴다.
 */
final class VisitBuffer {

    private static final Logger log = LoggerFactory.getLogger(VisitBuffer.class);

    static final int MAX_VISITORS = 200_000;
    static final int MAX_HOURS = 100;
    static final int MAX_ACTIVE = 200_000;
    static final int MAX_RAW = 100_000;
    static final int MAX_PAGES = 50_000;
    static final int MAX_REFERRERS = 20_000;
    static final int MAX_FLOWS = 100_000;
    static final int MAX_CAMPAIGNS = 20_000;
    /** 환경 묶음 — 하루 최대 3 × 7 × 6 × 5 = 630 줄이라 넉넉하다 */
    static final int MAX_ENV = 5_000;
    static final int MAX_SEARCH_TERMS = 20_000;
    static final int MAX_DAYS = 10;
    static final int MAX_BOTS = 100;

    record VisitorKey(LocalDate day, String visitorHash) {
    }

    /** 이 1분 안에 처음 본 시간(정시)과 신규 방문자 신호가 있었나 */
    record FirstSeen(Instant hour, boolean newVisitor) {
    }

    record PageKey(LocalDate day, String path, String kind) {
    }

    record ReferrerKey(LocalDate day, String group, String domain) {
    }

    /** 이동 — 세션 첫 페이지(「바로 들어옴」)는 {@code from = ""} */
    record FlowKey(LocalDate day, String from, String to) {
    }

    /** 세션 첫 페이지의 utm 묶음(빈 칸은 {@code ""}) — analytics V3 {@code analytics_campaign_daily} */
    record CampaignKey(LocalDate day, String source, String medium, String campaign) {
    }

    /** 환경 한 묶음(세션 첫 페이지 = 방문 1) — analytics V4 {@code analytics_env_daily} */
    record EnvKey(LocalDate day, String device, String browser, String os, String screen) {
    }

    /** 다듬은 검색어 — analytics V4 {@code analytics_search_daily} */
    record SearchKey(LocalDate day, String term) {
    }

    /** 검색 수 · 결과 없는 검색 수 */
    record SearchCount(long searches, long noResult) {

        SearchCount plus(SearchCount o) {
            return new SearchCount(searches + o.searches, noResult + o.noResult);
        }
    }

    /** 봇 종류({@link BotClassifier}) — analytics V4 {@code analytics_bot_daily} */
    record BotKey(LocalDate day, String kind) {
    }

    /** 원본 한 줄(원본 기록을 켰을 때만) */
    record RawView(long id, Instant viewedAt, String visitorHash, String sessionHash, String path, String kind, String prevPath, String referrerDomain,
                   String referrerGroup, String utmSource, String utmMedium, String utmCampaign, String device, boolean newVisitor) {
    }

    /**
     * 한 번에 넘기는 모음 — 넘긴 뒤에는 이 버퍼와 공유하지 않는다.
     *
     * @param dropped        이 1분 사이 상한 때문에 버린 줄 수(수집 진단)
     * @param ignoredBot     이 1분 사이 봇·빈 User-Agent 라 무시한 방문 신호 수(수집 진단)
     * @param ignoredInvalid 이 1분 사이 형식이 틀려 무시한 방문 신호 수(수집 진단)
     */
    record Batch(LinkedHashMap<VisitorKey, FirstSeen> visitors, LinkedHashMap<Instant, Long> pageViews, LinkedHashMap<String, Instant> active,
                 ArrayDeque<RawView> raw, LinkedHashMap<PageKey, Long> pages, LinkedHashMap<ReferrerKey, Long> referrers, LinkedHashMap<FlowKey, Long> flows,
                 LinkedHashMap<CampaignKey, Long> campaigns, LinkedHashMap<EnvKey, Long> env, LinkedHashMap<SearchKey, SearchCount> searches,
                 LinkedHashMap<LocalDate, Long> searchFiltered, LinkedHashMap<BotKey, Long> bots, long dropped, long ignoredBot, long ignoredInvalid) {

        /** 쓸 숫자가 없나(수집 진단 칸은 따로 — 비어도 「살아 있음」은 쓴다) */
        boolean isEmpty() {
            return visitors.isEmpty() && pageViews.isEmpty() && active.isEmpty() && raw.isEmpty() && pages.isEmpty() && referrers.isEmpty() && flows.isEmpty()
                    && campaigns.isEmpty() && env.isEmpty() && searches.isEmpty() && searchFiltered.isEmpty() && bots.isEmpty();
        }
    }

    private Batch current = empty();
    private long dropped;
    private long ignoredBot;
    private long ignoredInvalid;

    private static Batch empty() {
        return new Batch(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayDeque<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), 0, 0, 0);
    }

    // ── 넣기 ─────────────────────────────────────────────────────────────────

    synchronized void visit(LocalDate day, String visitorHash, Instant hour, boolean newVisitor, Instant seenAt) {
        current.visitors().merge(new VisitorKey(day, visitorHash), new FirstSeen(hour, newVisitor),
                (was, now) -> new FirstSeen(was.hour().isBefore(now.hour()) ? was.hour() : now.hour(), was.newVisitor() || now.newVisitor()));
        trim(current.visitors(), MAX_VISITORS);
        current.pageViews().merge(hour, 1L, Long::sum);
        trim(current.pageViews(), MAX_HOURS);
        current.active().merge(visitorHash, seenAt, (a, b) -> a.isAfter(b) ? a : b);
        trim(current.active(), MAX_ACTIVE);
    }

    synchronized void raw(RawView view, LocalDate day) {
        current.raw().addLast(view);
        while (current.raw().size() > MAX_RAW) {
            current.raw().pollFirst();
            dropped++;
        }
        current.pages().merge(new PageKey(day, view.path(), view.kind()), 1L, Long::sum);
        trim(current.pages(), MAX_PAGES);
    }

    synchronized void referrer(LocalDate day, String group, String domain) {
        current.referrers().merge(new ReferrerKey(day, group, domain == null ? "" : domain), 1L, Long::sum);
        trim(current.referrers(), MAX_REFERRERS);
    }

    /** 캠페인 방문 1 — 유입처({@link #referrer})와 같은 신호에서 모으고, 같은 몰아쓰기 트랜잭션으로 쓴다 */
    synchronized void campaign(LocalDate day, String source, String medium, String campaign) {
        current.campaigns().merge(new CampaignKey(day, source == null ? "" : source, medium == null ? "" : medium, campaign == null ? "" : campaign), 1L, Long::sum);
        trim(current.campaigns(), MAX_CAMPAIGNS);
    }

    synchronized void flow(LocalDate day, String from, String to) {
        current.flows().merge(new FlowKey(day, from, to), 1L, Long::sum);
        trim(current.flows(), MAX_FLOWS);
    }

    /** 환경 방문 1(세션 첫 페이지) */
    synchronized void env(LocalDate day, String device, String browser, String os, String screen) {
        current.env().merge(new EnvKey(day, device, browser, os, screen), 1L, Long::sum);
        trim(current.env(), MAX_ENV);
    }

    /** 검색 1(다듬은 검색어) */
    synchronized void search(LocalDate day, String term, boolean noResult) {
        current.searches().merge(new SearchKey(day, term), new SearchCount(1, noResult ? 1 : 0), SearchCount::plus);
        trim(current.searches(), MAX_SEARCH_TERMS);
    }

    /** 저장하지 않는 검색 1(개인정보 모양·길이) — 횟수만 */
    synchronized void searchFiltered(LocalDate day) {
        current.searchFiltered().merge(day, 1L, Long::sum);
        trim(current.searchFiltered(), MAX_DAYS);
    }

    /** 봇 요청 1 */
    synchronized void bot(LocalDate day, String kind) {
        current.bots().merge(new BotKey(day, kind), 1L, Long::sum);
        trim(current.bots(), MAX_BOTS);
    }

    /** 봇·빈 User-Agent 라 무시한 방문 신호 1(수집 진단) */
    synchronized void ignoredBot() {
        ignoredBot++;
    }

    /** 형식이 틀려 무시한 방문 신호 1(수집 진단) */
    synchronized void ignoredInvalid() {
        ignoredInvalid++;
    }

    // ── 넘기기·되돌리기 ───────────────────────────────────────────────────────

    /** 지금까지 모은 것을 통째로 넘기고 비운다. 그 사이 상한 때문에 버린 줄이 있으면 경고한다 */
    synchronized Batch drain() {
        Batch b = current;
        Batch out = new Batch(b.visitors(), b.pageViews(), b.active(), b.raw(), b.pages(), b.referrers(), b.flows(), b.campaigns(), b.env(), b.searches(),
                b.searchFiltered(), b.bots(), dropped, ignoredBot, ignoredInvalid);
        current = empty();
        if (dropped > 0) {
            log.warn("방문 신호 모음이 메모리 상한을 넘어 오래된 {}줄을 버렸다(이 서버의 지난 1분 숫자가 그만큼 덜 세졌다)", dropped);
        }
        dropped = 0;
        ignoredBot = 0;
        ignoredInvalid = 0;
        return out;
    }

    /** 쓰지 못한(트랜잭션째 되돌려진) 방문자·페이지뷰·지금 접속을 다음 번에 다시 쓰도록 되돌린다 */
    synchronized void restoreVisits(Batch b) {
        b.visitors().forEach((k, v) -> current.visitors().merge(k, v,
                (was, now) -> new FirstSeen(was.hour().isBefore(now.hour()) ? was.hour() : now.hour(), was.newVisitor() || now.newVisitor())));
        b.pageViews().forEach((k, v) -> current.pageViews().merge(k, v, Long::sum));
        b.active().forEach((k, v) -> current.active().merge(k, v, (x, y) -> x.isAfter(y) ? x : y));
        trim(current.visitors(), MAX_VISITORS);
        trim(current.pageViews(), MAX_HOURS);
        trim(current.active(), MAX_ACTIVE);
    }

    synchronized void restoreRaw(Batch b) {
        Iterator<RawView> it = b.raw().descendingIterator();
        while (it.hasNext()) {
            current.raw().addFirst(it.next());
        }
        while (current.raw().size() > MAX_RAW) {
            current.raw().pollFirst();
            dropped++;
        }
        b.pages().forEach((k, v) -> current.pages().merge(k, v, Long::sum));
        trim(current.pages(), MAX_PAGES);
    }

    /** 유입처와 캠페인은 한 트랜잭션으로 쓰므로 함께 되돌린다 */
    synchronized void restoreReferrers(Batch b) {
        b.referrers().forEach((k, v) -> current.referrers().merge(k, v, Long::sum));
        trim(current.referrers(), MAX_REFERRERS);
        b.campaigns().forEach((k, v) -> current.campaigns().merge(k, v, Long::sum));
        trim(current.campaigns(), MAX_CAMPAIGNS);
    }

    synchronized void restoreFlows(Batch b) {
        b.flows().forEach((k, v) -> current.flows().merge(k, v, Long::sum));
        trim(current.flows(), MAX_FLOWS);
    }

    synchronized void restoreEnv(Batch b) {
        b.env().forEach((k, v) -> current.env().merge(k, v, Long::sum));
        trim(current.env(), MAX_ENV);
    }

    /** 검색어와 걸러진 검색 수는 한 트랜잭션으로 쓰므로 함께 되돌린다 */
    synchronized void restoreSearches(Batch b) {
        b.searches().forEach((k, v) -> current.searches().merge(k, v, SearchCount::plus));
        trim(current.searches(), MAX_SEARCH_TERMS);
        b.searchFiltered().forEach((k, v) -> current.searchFiltered().merge(k, v, Long::sum));
        trim(current.searchFiltered(), MAX_DAYS);
    }

    synchronized void restoreBots(Batch b) {
        b.bots().forEach((k, v) -> current.bots().merge(k, v, Long::sum));
        trim(current.bots(), MAX_BOTS);
    }

    /** 상한을 넘으면 가장 오래 든 키부터 버린다(LinkedHashMap 은 넣은 차례) — lock 안 */
    private <K, V> void trim(Map<K, V> map, int max) {
        if (map.size() <= max) {
            return;
        }
        Iterator<K> it = map.keySet().iterator();
        while (map.size() > max && it.hasNext()) {
            it.next();
            it.remove();
            dropped++;
        }
    }
}
