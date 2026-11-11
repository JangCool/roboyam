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
                          int rawRetentionMonths, long activeNow) {
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

    // ── 2단계(site-analytics-contract 「2단계」) — 환경 · 검색어 · 봇 · 수집 진단 · 정보주체 요청 ─────────────

    /** 2단계 수집 스위치 — 각 칸은 「방문 수집 전체 && 그 항목」으로 접은 값({@code visits} = 봇 쪽 「사람 대비」 분모를 쓸 수 있나) */
    public record CollectMore(boolean enabled, boolean environment, boolean searchTerms, boolean bots, boolean visits) {
    }

    /** 분류 한 줄 — {@code visits} = 방문(세션 첫 페이지) 수 */
    public record ShareRow(String key, long visits) {
    }

    /** 일별 기기 한 칸 — {@code devices} = MOBILE · TABLET · DESKTOP 모두(없으면 0) */
    public record DeviceSeriesPoint(String date, Map<String, Long> devices) {
    }

    /** {@code GET …/visits/environment} — 기기·브라우저·OS 는 많은 차례, 화면 너비는 구간 차례 */
    public record Environment(Range range, Instant asOf, CollectMore collect, long total, List<ShareRow> devices, List<ShareRow> browsers, List<ShareRow> oses,
                              List<ShareRow> screens, List<DeviceSeriesPoint> series) {
    }

    public record SearchTerm(String term, long searches, long noResult) {
    }

    /** 결과 없는 검색어 — {@code searches} = 결과 없이 끝난 검색 수, {@code lastDay} = 마지막으로 결과 없던 날(한국 날짜) */
    public record NoResultTerm(String term, long searches, String lastDay) {
    }

    public record SearchTotals(long searches, long noResult, long terms, long filtered) {
    }

    /** {@code GET …/visits/search} */
    public record Search(Range range, Instant asOf, CollectMore collect, SearchTotals totals, List<SearchTerm> terms, List<NoResultTerm> noResultTerms) {
    }

    /** 봇 요청 · 사람 페이지뷰(방문자·페이지뷰 수집이 꺼져 있으면 null) */
    public record BotCounts(long bots, Long humans) {
    }

    public record BotKindCount(String kind, long requests) {
    }

    /** 일별 봇 한 칸 — {@code kinds} = 여섯 종류 모두(없으면 0) */
    public record BotSeriesPoint(String date, Map<String, Long> kinds) {
    }

    /** {@code GET …/visits/bots} */
    public record Bots(Range range, Instant asOf, CollectMore collect, BotCounts today, BotCounts totals, List<BotKindCount> kinds, List<BotSeriesPoint> series) {
    }

    /** 수집 진단의 문제 한 줄 — {@code level} ERROR · WARN */
    public record HealthIssue(String code, String level, String instance, String job, Long count, Instant since) {
    }

    /** 서버 한 줄(최근 24시간) — {@code ignoredExcluded} 뺄 계정(운영자 회원 계정·AI 회원) 신호라 버린 수 · {@code unknownPath} 「알 수 없는 주소」로 묶은 신호 수(3단계) */
    public record HealthInstance(String instance, Instant lastWriteAt, long flushOk, long flushFail, long dropped, long ignoredBot, long ignoredInvalid,
                                 long ignoredExcluded, long unknownPath) {
    }

    public record HealthFailure(Instant at, String instance, String reason, String detail) {
    }

    /** 크론 한 줄 — 배치 로그의 마지막 실행. {@code cron} = pg_cron 의 식(배치 관리 화면과 같은 값) */
    public record HealthCron(String job, String cron, Instant lastRunAt, String lastStatus, Long processed, String message, boolean late) {
    }

    /** 원본 보관 — {@code retentionMonths} 보관 개월 수(이번 달 포함, 3단계 — 예전 {@code retentionDays}) */
    public record HealthRaw(boolean rawEvents, int retentionMonths, String oldestDay, int partitions, Long approxRows, boolean nextPartitionReady) {
    }

    /** {@code GET …/visits/health} — 기간 없음(최근 24시간). {@code status} OK · DELAYED · STOPPED · OFF */
    public record Health(Instant asOf, String status, List<HealthIssue> issues, List<HealthInstance> instances, List<HealthFailure> failures, List<HealthCron> crons,
                         HealthRaw raw) {
    }

    /** 정보주체 요청 한 줄 — {@code status} PENDING · DONE, 대기면 {@code deletedRows}·{@code processedAt} null */
    public record ForgetRequest(String id, Instant receivedAt, String status, Long deletedRows, Instant processedAt) {
    }

    /** {@code GET …/visits/requests} — 최신 차례 */
    public record ForgetPage(List<ForgetRequest> items, long total, long pending, long last30Days) {
    }
}
