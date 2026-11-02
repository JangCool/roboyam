package com.community.ad.dto;

import java.time.Instant;
import java.util.List;

/**
 * 광고 성과 응답 중 사업자·운영자가 같이 쓰는 모양(ad-performance-contract 「API」 — user-web {@code useBusinessApi.types.ts}·admin-web {@code useAdApi.types.ts} 의
 * {@code AdApplicationStats}). 날짜는 한국 날짜 {@code YYYY-MM-DD}(끝 포함), 시각은 ISO 순간. 클릭률은 화면이 계산한다.
 */
public final class AdStatsView {

    private AdStatsView() {
    }

    /** 노출·클릭 수 */
    public record Counts(long impressions, long clicks) {

        public static final Counts ZERO = new Counts(0, 0);

        public Counts plus(long impressions, long clicks) {
            return new Counts(this.impressions + impressions, this.clicks + clicks);
        }

        public Counts plus(Counts other) {
            return plus(other.impressions, other.clicks);
        }

        public boolean empty() {
            return impressions == 0 && clicks == 0;
        }
    }

    /** 날짜 범위(한국 날짜, 끝 포함) */
    public record Range(String from, String to) {
    }

    /** 하루 — {@code state} SHOWING·PAUSED·TAKEN_DOWN */
    public record Day(String date, long impressions, long clicks, String state) {
    }

    /** 자리 한 줄 — 자리 값은 V118 {@code ck_ads_placement} 아홉 */
    public record PlacementRow(String placement, long impressions, long clicks) {
    }

    /** 소재 버전 한 줄 — {@code current} = 최신 승인 버전, 승인 기록이 없으면 {@code approvedAt} null */
    public record VersionRow(int version, Instant approvedAt, boolean current, long impressions, long clicks) {
    }

    /** 멈춘 구간 — 지금 멈춰 있으면 {@code to} null, {@code cause} 내림 까닭(EMERGENCY·SUSPENDED·ADVERTISER_PAUSE·ADVERTISER_STOP) */
    public record Downtime(Instant from, Instant to, String cause) {
    }

    /**
     * 한 건의 성과({@code AdApplicationStats}). {@code exposureStop} = 사업자 핫딜 글 건이 진행 중인데 노출 자리가 지금 나가지 않는 까닭
     * ({@code DEAL_ENDED}·{@code POST_HIDDEN}·{@code PREFIX_MISSING}·{@code NOT_POSTED} — {@code BizDealPostReader.exposureStop}), 아니면 null
     */
    public record Application(Range range, Instant asOf, Counts totals, List<Day> days, List<PlacementRow> byPlacement, List<VersionRow> byVersion,
                              List<Downtime> downtimes, String exposureStop) {
    }
}
