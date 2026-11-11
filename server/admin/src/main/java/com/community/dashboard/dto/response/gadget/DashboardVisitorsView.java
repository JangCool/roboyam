package com.community.dashboard.dto.response.gadget;

import java.util.List;

/**
 * {@code VISITORS} — 오늘(한국 날짜) 방문자(중복 없음)·신규 방문자·페이지뷰 · 지금 접속(최근 5분) · 어제 같은 시각까지 · 오늘 시간대별(0시부터 지금 시각까지, 시간 칸 집계).
 * 「어제 같은 시각까지」는 시간 칸 단위다 — 어제 0시부터 지금 시각이 든 시간 칸(그 칸 전체)까지의 합.
 * {@code visitorsThisWeek}·{@code visitorsThisMonth} = 이번 주(월요일부터)·이번 달 순방문자(방문자, 3단계 결정 ④ — {@code GET …/visits/uniques} 의 VISITOR 이번 기간과 같은 수,
 * 세기 시작 전이면 null).
 */
public record DashboardVisitorsView(long visitorsToday, long newVisitorsToday, long pageViewsToday, long activeNow, long visitorsYesterdaySameTime,
                                    long pageViewsYesterdaySameTime, List<Hour> hours, Long visitorsThisWeek, Long visitorsThisMonth) implements DashboardGadgetData {

    /** 3단계 전 모양(순방문자 없음 — null) */
    public DashboardVisitorsView(long visitorsToday, long newVisitorsToday, long pageViewsToday, long activeNow, long visitorsYesterdaySameTime,
                                 long pageViewsYesterdaySameTime, List<Hour> hours) {
        this(visitorsToday, newVisitorsToday, pageViewsToday, activeNow, visitorsYesterdaySameTime, pageViewsYesterdaySameTime, hours, null, null);
    }

    /** {@code hour} = 한국 시각 0~23 */
    public record Hour(int hour, long visitors) {
    }
}
