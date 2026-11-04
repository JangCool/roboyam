package com.community.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 「방문자 통계」 응답(관리자 {@code GET /api/admin/analytics/visits/**}) — 모양·칸 뜻의 원천은 admin-web {@code useVisitAnalyticsApi.types.ts}(이름이 같다).
 * 날짜는 한국 날짜 글자({@code YYYY-MM-DD}), 시각은 UTC ISO. 모든 숫자는 집계 표의 합이다.
 */
public final class VisitAnalyticsAdmin {

    private VisitAnalyticsAdmin() {
    }

    public record Range(String from, String to) {
    }

    /** 수집 스위치 — 각 칸은 「방문 수집 전체 && 그 항목」으로 접은 값 */
    public record Collect(boolean enabled, boolean visits, boolean rawEvents, boolean referrers, boolean flows) {
    }

    public record Counts(long visitors, long newVisitors, long pageViews) {
    }

    public record PreviousCounts(Range range, long visitors, long newVisitors, long pageViews) {
    }

    /** {@code at} = HOUR 면 정시 시각(ISO), DAY 면 한국 날짜 */
    public record SeriesPoint(String at, long visitors, long newVisitors, long pageViews) {
    }

    /** {@code GET …/visits/summary} */
    public record Summary(Range range, Instant asOf, String granularity, Counts totals, PreviousCounts previous, List<SeriesPoint> series, Collect collect,
                          int rawRetentionDays, long activeNow) {
    }

    public record PageRow(String path, String kind, String title, String titleEn, long views) {
    }

    /** {@code GET …/visits/pages} */
    public record PagePage(List<PageRow> items, long total, long totalViews) {
    }

    public record DayViews(String date, long views) {
    }

    /** 이동의 한쪽 끝 — 들어온 곳 {@code path} 가 null 이면 「바로 들어옴」 */
    public record FlowEnd(String path, String title, String titleEn, long moves) {
    }

    /** {@code GET …/visits/pages/detail} */
    public record PageDetail(String path, String kind, String title, String titleEn, long views, List<DayViews> days, List<FlowEnd> incoming, long incomingTotal,
                             List<FlowEnd> outgoing, long outgoingTotal) {
    }

    public record GroupCount(String group, long visits) {
    }

    public record ReferrerDomain(String domain, long visits) {
    }

    /** 캠페인 한 줄 — 빈 utm 칸은 null */
    public record CampaignRow(String source, String medium, String campaign, long visits) {
    }

    /** {@code groups} = 다섯 분류 모두(없으면 0), 화면 차례 */
    public record ReferrerSeriesPoint(String date, Map<String, Long> groups) {
    }

    /** {@code GET …/visits/referrers} — {@code campaigns} 는 늘 배열({@code detail=false} 면 빈 배열) */
    public record Referrers(long total, List<GroupCount> groups, List<ReferrerDomain> domains, List<CampaignRow> campaigns, List<ReferrerSeriesPoint> series) {
    }

    public record FlowMove(String from, String to, long moves) {
    }

    /** {@code GET …/visits/flows} */
    public record Flows(String path, String kind, String title, String titleEn, List<FlowEnd> incoming, long incomingTotal, List<FlowEnd> outgoing,
                        long outgoingTotal, List<FlowMove> top) {
    }

    /** 요일(1 월 ~ 7 일)·시(0~23, 한국) 칸 — 합과 더한 칸 수 */
    public record TimeCell(int weekday, int hour, long visitors, long pageViews, int days) {
    }

    /** {@code GET …/visits/time} */
    public record Time(Range range, Instant asOf, List<TimeCell> cells) {
    }

    public record MinutePoint(Instant at, long pageViews) {
    }

    /** {@code GET …/visits/realtime} */
    public record Realtime(Instant asOf, Collect collect, long activeNow, List<MinutePoint> perMinute, List<PageRow> pages, List<GroupCount> referrers) {
    }
}
