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
 *
 * <p><b>메모리 상한</b>: 칸마다 줄 수 상한이 있고, 넘으면 <b>가장 오래 든 것부터 버리고</b> 1분에 한 번 경고 로그를 남긴다(방문 폭주·꾸민 신호로 메모리가 터지지 않게).
 * 버린 만큼 숫자가 덜 세진다 — 정상 방문량에서는 닿지 않는 값이다(분당 신호 기준).
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

    record VisitorKey(LocalDate day, String visitorHash) {
    }

    /** 이 1분 안에 처음 본 시간(정시)과 신규 방문자 신호가 있었나 */
    record FirstSeen(Instant hour, boolean newVisitor) {
    }

    record PageKey(LocalDate day, String path, String kind) {
    }

    record ReferrerKey(LocalDate day, String group, String domain) {
    }

    record FlowKey(LocalDate day, String from, String to) {
    }

    /** 원본 한 줄(원본 기록을 켰을 때만) */
    record RawView(long id, Instant viewedAt, String visitorHash, String sessionHash, String path, String kind, String prevPath, String referrerDomain,
                   String referrerGroup, String utmSource, String utmMedium, String utmCampaign, String device, boolean newVisitor) {
    }

    /** 한 번에 넘기는 모음 — 넘긴 뒤에는 이 버퍼와 공유하지 않는다 */
    record Batch(LinkedHashMap<VisitorKey, FirstSeen> visitors, LinkedHashMap<Instant, Long> pageViews, LinkedHashMap<String, Instant> active,
                 ArrayDeque<RawView> raw, LinkedHashMap<PageKey, Long> pages, LinkedHashMap<ReferrerKey, Long> referrers, LinkedHashMap<FlowKey, Long> flows) {

        boolean isEmpty() {
            return visitors.isEmpty() && pageViews.isEmpty() && active.isEmpty() && raw.isEmpty() && pages.isEmpty() && referrers.isEmpty() && flows.isEmpty();
        }
    }

    private Batch current = empty();
    private long dropped;

    private static Batch empty() {
        return new Batch(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayDeque<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>());
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

    synchronized void flow(LocalDate day, String from, String to) {
        current.flows().merge(new FlowKey(day, from, to), 1L, Long::sum);
        trim(current.flows(), MAX_FLOWS);
    }

    // ── 넘기기·되돌리기 ───────────────────────────────────────────────────────

    /** 지금까지 모은 것을 통째로 넘기고 비운다. 그 사이 상한 때문에 버린 줄이 있으면 경고한다 */
    synchronized Batch drain() {
        Batch out = current;
        current = empty();
        if (dropped > 0) {
            log.warn("방문 신호 모음이 메모리 상한을 넘어 오래된 {}줄을 버렸다(이 서버의 지난 1분 숫자가 그만큼 덜 세졌다)", dropped);
            dropped = 0;
        }
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

    synchronized void restoreReferrers(Batch b) {
        b.referrers().forEach((k, v) -> current.referrers().merge(k, v, Long::sum));
        trim(current.referrers(), MAX_REFERRERS);
    }

    synchronized void restoreFlows(Batch b) {
        b.flows().forEach((k, v) -> current.flows().merge(k, v, Long::sum));
        trim(current.flows(), MAX_FLOWS);
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
