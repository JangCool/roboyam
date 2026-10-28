package com.community.groupbuy.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 「설정 › 커머스」의 공구 구획(배송비 정책·택배사·사업자 정보·판매 스위치·공구 설정)과 대시보드 커머스 칸의 응답(admin-web {@code useCommerceSettingsApi.types.ts}·
 * {@code useCommerceDashboardApi.types.ts} 와 같은 모양).
 */
public final class CommerceGroupBuySettingsView {

    private CommerceGroupBuySettingsView() {
    }

    public record ShippingPolicyLine(String id, String code, String name, long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode, Instant effectiveFrom,
                                     Instant effectiveTo, boolean started, boolean current, String createdBy, Instant createdAt) {
    }

    public record ShippingPolicies(List<ShippingPolicyLine> items) {
    }

    public record CarrierLine(String code, String name, String trackingUrlTemplate, boolean active, boolean used) {
    }

    public record Carriers(List<CarrierLine> items, Instant updatedAt, String updatedBy) {
    }

    public record BusinessValues(String companyName, String representative, String businessNo, String mailOrderNo, String mailOrderAuthority, String address, String phone,
                                 String email, String businessHours, String privacyOfficer, String hostingProvider, String shippingRefundPolicy) {
    }

    public record BusinessInfo(BusinessValues values, boolean ready, Instant updatedAt, String updatedBy) {
    }

    public record AutoCheck(String key, boolean ok) {
    }

    public record ChecklistItem(String key, boolean checked, String checkedBy, Instant checkedAt) {
    }

    public record Sales(boolean enabled, List<AutoCheck> autoChecks, List<ChecklistItem> checklist, boolean canEnable, Instant changedAt, String changedBy) {
    }

    public record Range(long min, long max) {
    }

    public record SettingsValues(long holdMinutes, long thresholdPeriodMaxDays, long firstComePeriodMaxDays, long extensionMaxMinutes, long extensionWindowMinutes,
                                 long judgeGraceMinutes, long compensationCap, long largeRefundThreshold, long addressRevealMinutes, long expiredAddressRetentionDays,
                                 long shipWithinBusinessDays, long supplyChoiceBusinessDays, List<LocalDate> holidays) {
    }

    public record Settings(SettingsValues values, Map<String, Long> defaults, Map<String, Range> ranges, Instant updatedAt, String updatedBy) {
    }

    public record OpenRequests(long total, long warn, long critical) {
    }

    public record DeliveryStale(long count, int days) {
    }

    /** 볼 권한이 없는 칸은 null */
    public record Dashboard(Long preparingWaiting, OpenRequests openRequests, Long paymentAnomalies, Long expiringHolds, Long lowStockGroupBuys, Long shipOverdue,
                            DeliveryStale deliveryStale, Instant generatedAt) {
    }
}
