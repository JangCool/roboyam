package com.community.bizapplication.dto;

import com.community.ad.dto.AdStatsView;
import java.time.Instant;
import java.util.List;

/**
 * 사업자 센터 「진행 중 광고」 요약({@code GET /api/business/ads/stats-summary} — user-web {@code BizAdStatsSummary}, ad-performance-contract 규칙 8). 한 건 성과
 * ({@code GET /api/business/applications/{id}/stats})는 shared {@link AdStatsView.Application}.
 */
public final class BizAdStatsView {

    private BizAdStatsView() {
    }

    /**
     * 한 줄 — {@code kind}·{@code adType}·{@code placement}·{@code status}·{@code phase}·{@code takedownCause}·{@code startDate}·{@code endDate} 는 신청 건 요약
     * {@link BizMemberView.Summary} 와 같은 뜻. {@code last7} = 오늘 포함 최근 7일 합, {@code spark} = 그 7일 날짜별(오래된 차례, 기간 밖 날은 0·SHOWING).
     * {@code exposureStop} = 진행 중 사업자 핫딜 글 건의 노출 자리가 지금 나가지 않는 까닭(딜 종료 등 — {@link AdStatsView.Application#exposureStop}), 아니면 null.
     */
    public record Item(String id, String title, String kind, String adType, String placement, String status, String phase, String takedownCause, String startDate,
                       String endDate, AdStatsView.Counts last7, List<AdStatsView.Day> spark, String exposureStop) {
    }

    public record Summary(Instant asOf, List<Item> items) {
    }
}
