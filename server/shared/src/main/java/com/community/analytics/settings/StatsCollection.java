package com.community.analytics.settings;

import java.util.List;

/**
 * 「설정 › 통계 수집」 값(shared V133 {@code stats_collection_settings} 한 행 + V135 의 2단계 세 칸 + V193 의 원본 보관 개월 수) — docs/features/site-analytics-contract.md
 * 「설정」·「2단계」·「3단계 결정 ①」. <b>모두 기본 꺼짐</b>, 원본 보관 2개월(이번 달 포함 — 이번 달과 지난달).
 *
 * <p>방문 하위 항목({@code visits}·{@code rawEvents}·{@code referrers}·{@code flows}·{@code environment}·{@code searchTerms}·{@code bots})은 {@code enabled}(방문 수집 전체)가
 * 꺼지면 동작하지 않는다 — 값은 그대로 둔다. 동작 여부는 {@code …On()} 으로 읽는다(저장 값 그대로가 아니라).
 *
 * <p>원본 보관은 <b>달 단위</b>({@link #RETENTION_MONTHS}, 이번 달 포함): 크론이 「이번 달부터 n번째 앞 달보다 오래된 달 조각」을 통째로 지운다(R__analytics_10). 옛 칸
 * {@code raw_retention_days} 는 롤링 배포 동안 옛 서버가 읽도록 남겨 두었을 뿐 새 코드는 읽지도 쓰지도 않는다(다음 버전에 지운다).
 */
public record StatsCollection(boolean enabled, boolean visits, boolean rawEvents, int rawRetentionMonths, boolean referrers, boolean flows, boolean environment,
                              boolean searchTerms, boolean bots, boolean notificationSends, boolean boardActivity) {

    /** 고를 수 있는 원본 보관 개월 수(이번 달 포함) */
    public static final List<Integer> RETENTION_MONTHS = List.of(2, 3, 6, 12);
    public static final int DEFAULT_RETENTION_MONTHS = 2;

    /** 행이 없거나 읽지 못했을 때 — 모두 꺼짐 */
    public static final StatsCollection OFF = new StatsCollection(false, false, false, DEFAULT_RETENTION_MONTHS, false, false, false, false, false, false, false);

    /** 1단계 칸만 주는 모양(2단계 세 칸 {@code environment}·{@code searchTerms}·{@code bots} = 꺼짐) — 1단계 시험·호출이 그대로 쓴다 */
    public StatsCollection(boolean enabled, boolean visits, boolean rawEvents, int rawRetentionMonths, boolean referrers, boolean flows, boolean notificationSends,
                           boolean boardActivity) {
        this(enabled, visits, rawEvents, rawRetentionMonths, referrers, flows, false, false, false, notificationSends, boardActivity);
    }

    /** 방문자·페이지뷰·지금 접속 */
    public boolean visitsOn() {
        return enabled && visits;
    }

    /** 방문 원본 기록({@code analytics_page_views}) — 실시간 화면·수집 진단이 읽는다 */
    public boolean rawEventsOn() {
        return enabled && rawEvents;
    }

    /**
     * 페이지별 일별 조회({@code analytics_page_daily} — 인기 페이지·페이지 순위·서랍 일별). 3단계부터 「원본 기록」이 아니라 「방문자·페이지뷰」({@code visits})에 딸린다
     * (주소가 정해진 묶음이 되어 작아졌으므로 원본을 꺼도 페이지 순위가 쌓인다 — site-analytics-contract 「3단계 결정 ②」).
     */
    public boolean pagesOn() {
        return visitsOn();
    }

    public boolean referrersOn() {
        return enabled && referrers;
    }

    public boolean flowsOn() {
        return enabled && flows;
    }

    /** 환경(브라우저·OS·화면 너비 분류값 + 서버가 가른 기기) — 세션 첫 페이지 신호 */
    public boolean environmentOn() {
        return enabled && environment;
    }

    /** 우리 사이트 검색어 — 신호가 아니라 검색 요청에서 센다 */
    public boolean searchTermsOn() {
        return enabled && searchTerms;
    }

    /** 봇·크롤러 요청 수 — 신호가 아니라 화면 HTML 요청에서 센다 */
    public boolean botsOn() {
        return enabled && bots;
    }

    /** 방문 신호를 하나라도 쓰는가 — 공개 {@code /api/site/info} 의 브라우저 스위치와 같은 판단(검색어·봇은 신호가 아니라 빠진다) */
    public boolean anyVisitOn() {
        return visitsOn() || rawEventsOn() || referrersOn() || flowsOn() || environmentOn();
    }

    /** 방문 쪽 수집(신호 + 검색어 + 봇)이 하나라도 동작하는가 — 수집 진단의 「꺼짐」 판정 */
    public boolean anyCollectOn() {
        return anyVisitOn() || searchTermsOn() || botsOn();
    }
}
