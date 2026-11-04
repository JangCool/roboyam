package com.community.analytics.settings;

import java.util.List;

/**
 * 「설정 › 통계 수집」 값(shared V133 {@code stats_collection_settings} 한 행 + V135 의 2단계 세 칸) — docs/features/site-analytics-contract.md 「설정」·「2단계」.
 * <b>모두 기본 꺼짐</b>, 원본 보관 90일.
 *
 * <p>방문 하위 항목({@code visits}·{@code rawEvents}·{@code referrers}·{@code flows}·{@code environment}·{@code searchTerms}·{@code bots})은 {@code enabled}(방문 수집 전체)가
 * 꺼지면 동작하지 않는다 — 값은 그대로 둔다. 동작 여부는 {@code …On()} 으로 읽는다(저장 값 그대로가 아니라).
 */
public record StatsCollection(boolean enabled, boolean visits, boolean rawEvents, int rawRetentionDays, boolean referrers, boolean flows, boolean environment,
                              boolean searchTerms, boolean bots, boolean notificationSends, boolean boardActivity) {

    /** 고를 수 있는 원본 보관 일수 */
    public static final List<Integer> RETENTION_DAYS = List.of(30, 90, 180, 365);
    public static final int DEFAULT_RETENTION_DAYS = 90;

    /** 행이 없거나 읽지 못했을 때 — 모두 꺼짐 */
    public static final StatsCollection OFF = new StatsCollection(false, false, false, DEFAULT_RETENTION_DAYS, false, false, false, false, false, false, false);

    /** 1단계 칸만 주는 모양(2단계 세 칸 {@code environment}·{@code searchTerms}·{@code bots} = 꺼짐) — 1단계 시험·호출이 그대로 쓴다 */
    public StatsCollection(boolean enabled, boolean visits, boolean rawEvents, int rawRetentionDays, boolean referrers, boolean flows, boolean notificationSends,
                           boolean boardActivity) {
        this(enabled, visits, rawEvents, rawRetentionDays, referrers, flows, false, false, false, notificationSends, boardActivity);
    }

    /** 방문자·페이지뷰·지금 접속 */
    public boolean visitsOn() {
        return enabled && visits;
    }

    /** 방문 원본 기록 + 페이지별 일별 조회(인기 페이지) */
    public boolean rawEventsOn() {
        return enabled && rawEvents;
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
