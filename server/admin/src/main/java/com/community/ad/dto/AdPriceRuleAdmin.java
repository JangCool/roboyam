package com.community.ad.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 상품 가격(요금표 예약 변경) 관리자 API 모양 — docs/features/ad-price-rules-contract.md, admin-web {@code useAdApi.types.ts} 「상품 가격」 절({@code AdPriceRulesView}·{@code AdPriceRule}·
 * {@code AdPriceSchedule}·{@code AdPriceScheduleRequest}·{@code AdPriceBulkScheduleRequest}·{@code AdPriceOccupancy}·{@code AdPriceRecentChange}). 변경 이력 쪽은 광고 노출 자리 이력과
 * 같은 모양({@link AdPlacementHistory.Page})이다.
 */
public final class AdPriceRuleAdmin {

    private AdPriceRuleAdmin() {
    }

    // ── 요청 ──────────────────────────────────────────────────────────────

    /**
     * 상품 하나의 예약 만들기·고치기 본문. 숫자는 정수 검사를 하려고 {@link BigDecimal} 로 받는다(소수는 400).
     *
     * @param effectiveAt 적용 시각(순간 ISO-8601)
     * @param capacity    동시 한도 — 하루 단위 상품은 1 이상, 건당(POST) 상품은 null
     */
    public record ScheduleRequest(String effectiveAt, BigDecimal unitPrice, BigDecimal capacity, BigDecimal maxDays, BigDecimal maxLeadDays) {
    }

    /** 단가 일괄 조정 — {@code kind} PERCENT(−90~300) · AMOUNT(원), {@code value} 정수 */
    public record Adjust(String kind, BigDecimal value) {
    }

    /** 여러 상품 한꺼번에 — 고른 상품마다 예약 1건(단가만 바꿈) */
    public record BulkRequest(String effectiveAt, List<String> products, Adjust adjust) {
    }

    public record AcceptingRequest(Boolean accepting) {
    }

    // ── 응답 ──────────────────────────────────────────────────────────────

    /** 운영자(지워졌으면 이름은 요청 언어의 「삭제된 운영자」) */
    public record Operator(String id, String name) {
    }

    /** 걸린 예약 한 건(아직 적용 전) */
    public record Schedule(String id, Instant effectiveAt, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, Operator createdBy, Instant createdAt,
                           Instant updatedAt) {
    }

    /** 앞날 겹침 분포 한 칸 — 최대 동시 겹침이 {@code peak} 인 날이 {@code days} 일 */
    public record Occupancy(int peak, int days) {
    }

    /** 최근 변경의 칸 하나 — {@code field} 는 칸 이름만({@code unitPrice}·{@code capacity}·{@code maxDays}·{@code maxLeadDays}·{@code accepting}) */
    public record RecentChangeField(String field, String before, String after) {
    }

    /** 그 상품의 최근 적용된 변경 한 건 — 적용 시각이 지난 기간표 줄({@code changedAt} = 적용 시각, 사람 = 그 줄을 마지막으로 손댄 운영자) 또는 받기 켬/끔 */
    public record RecentChange(String id, Instant changedAt, String operatorName, List<RecentChangeField> changes) {
    }

    /** 요금표 한 줄 — 값은 지금 적용되는 값(적용 시각이 지난 예약 포함) */
    public record Rule(String product, String unit, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, boolean accepting, List<Occupancy> occupancy,
                       List<Schedule> schedules, List<RecentChange> recentChanges) {
    }

    /** {@code GET /api/admin/ads/price-rules}(쓰기 API 도 이 모양) — 지금 값이 마지막으로 바뀐 때(가장 늦게 적용된 기간표 줄 · 받기 켬/끔), 바뀐 적 없으면 null */
    public record View(List<Rule> rules, Instant updatedAt, Operator updatedBy) {
    }
}
