package com.community.site.dto.response;

import com.community.analytics.settings.StatsCollection;

/**
 * {@code /api/site/info} 의 방문 수집 스위치(site-analytics-contract 「API」·「2단계」) — 브라우저는 {@code enabled} 이고 하나라도 켜졌을 때만 {@code POST /api/visits} 를 보낸다.
 * 하위 칸은 <b>동작 여부</b>다(방문 수집 전체가 꺼지면 저장 값과 상관없이 false). 서버도 같은 판단으로 다시 거른다.
 * {@code environment} 가 켜졌으면 브라우저는 세션 첫 페이지 신호에 {@code env}(브라우저·OS·화면 너비 분류값)를 싣는다. 검색어·봇은 서버가 세므로 여기 없다.
 */
public record SiteAnalyticsView(boolean enabled, boolean visits, boolean rawEvents, boolean referrers, boolean flows, boolean environment) {

    public static SiteAnalyticsView of(StatsCollection s) {
        return new SiteAnalyticsView(s.enabled(), s.visitsOn(), s.rawEventsOn(), s.referrersOn(), s.flowsOn(), s.environmentOn());
    }
}
