package com.community.site.dto.response;

import com.community.analytics.settings.StatsCollection;

/**
 * {@code /api/site/info} 의 방문 수집 스위치(site-analytics-contract 「API」) — 브라우저는 {@code enabled} 이고 하나라도 켜졌을 때만 {@code POST /api/visits} 를 보낸다.
 * 하위 칸은 <b>동작 여부</b>다(방문 수집 전체가 꺼지면 저장 값과 상관없이 false). 서버도 같은 판단으로 다시 거른다.
 */
public record SiteAnalyticsView(boolean enabled, boolean visits, boolean rawEvents, boolean referrers, boolean flows) {

    public static SiteAnalyticsView of(StatsCollection s) {
        return new SiteAnalyticsView(s.enabled(), s.visitsOn(), s.rawEventsOn(), s.referrersOn(), s.flowsOn());
    }
}
