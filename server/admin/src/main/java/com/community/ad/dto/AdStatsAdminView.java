package com.community.ad.dto;

import com.community.ad.service.AdAdvertiserService;
import com.community.bizapplication.dto.BizAdminView;
import java.time.Instant;
import java.util.List;

/**
 * 운영자 광고 성과(admin-web {@code useAdApi.types.ts} 「광고 성과」 — {@code AdStatsOverview}·{@code AdStatsBookingPage}, ad-performance-contract 규칙 9·10). 한 건 성과는
 * shared {@link AdStatsView.Application}.
 */
public final class AdStatsAdminView {

    private AdStatsAdminView() {
    }

    /** 광고주 — 일정표 광고주 ID({@code adv-}·{@code biz-}) · 이름 · ADVERTISER·BUSINESS */
    public record Advertiser(String id, String name, String kind) {

        public static Advertiser of(AdAdvertiserService.Option o) {
            return new Advertiser(o.id(), o.name(), o.kind());
        }
    }

    /** 합계 — {@code bookings} = 범위 안 노출 &gt; 0 인 건 수 */
    public record Totals(long impressions, long clicks, long bookings) {
    }

    /** 날짜 하나(합계 — 건마다 상태가 달라 {@code state} 없음) */
    public record DayTotal(String date, long impressions, long clicks) {
    }

    public record TypeRow(String type, long impressions, long clicks, long bookings) {
    }

    public record AdvertiserRow(Advertiser advertiser, long impressions, long clicks, long bookings) {
    }

    /** {@code GET /api/admin/ads/stats} — {@code byType}·{@code byPlacement}·{@code topAdvertisers} 는 0 인 줄을 빼고 노출 차례, 광고주는 상위 10 */
    public record Overview(Instant asOf, AdStatsView.Range range, Advertiser advertiser, Totals totals, List<DayTotal> days, List<TypeRow> byType,
                           List<AdStatsView.PlacementRow> byPlacement, List<AdvertiserRow> topAdvertisers) {
    }

    /** 건별 한 줄 — 일정표 {@link AdAdminView.Row} 칸 그대로 + 범위 합 + {@code exposureStop}(진행 중 사업자 핫딜 글 건의 노출 멈춤 까닭 — {@link AdStatsView.Application#exposureStop}) */
    public record BookingRow(String id, String type, AdAdvertiserService.Option advertiser, BizAdminView.Period period, String status, String paymentMethod, int weight,
                             String source, String applicationNo, Instant lastActionAt, String domain, String takedownCause, long impressions, long clicks, String exposureStop) {

        public static BookingRow of(AdAdminView.Row r, long impressions, long clicks, String exposureStop) {
            return new BookingRow(r.id(), r.type(), r.advertiser(), r.period(), r.status(), r.paymentMethod(), r.weight(), r.source(), r.applicationNo(),
                    r.lastActionAt(), r.domain(), r.takedownCause(), impressions, clicks, exposureStop);
        }
    }

    public record BookingPage(List<BookingRow> items, long total) {
    }
}
