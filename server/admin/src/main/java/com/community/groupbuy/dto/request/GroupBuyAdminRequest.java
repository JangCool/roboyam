package com.community.groupbuy.dto.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 공구 API 의 요청 본문(admin-web {@code useGroupBuyAdminApi.types.ts}·{@code useGroupBuyProductApi.types.ts}·{@code useGroupBuyOrderApi.types.ts}·
 * {@code useGroupBuyRequestApi.types.ts}·{@code useCommerceSettingsApi.types.ts} 와 같은 모양). ID 는 글자로 받는다.
 */
public final class GroupBuyAdminRequest {

    private GroupBuyAdminRequest() {
    }

    // ── 공구 ──
    public record SkuInput(String productSkuId, Long extraPrice, Integer maxQty, Boolean onSale, Long supplyPrice) {
    }

    public record AddonInput(String productSkuId, Long price, Integer maxQty, Long supplyPrice) {
    }

    public record Save(String productId, String title, Long groupPrice, String shippingPolicyCode, Integer minQty, Integer maxQty, Integer maxQtyPerUser, Instant startAt,
                       Instant endAt, String extensionMode, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays, String returnPolicy,
                       Boolean roundLimitShared, List<SkuInput> skus, List<AddonInput> addons, Boolean marginConfirmed, Integer version) {
    }

    public record SupplyPrice(String skuId, Long supplyPrice) {
    }

    public record Content(List<SupplyPrice> supplyPrices, Boolean marginConfirmed, Integer version) {
    }

    public record Start(Boolean marginConfirmed) {
    }

    public record Reason(String reason) {
    }

    public record DelayNotice(LocalDate newPlannedAt, String reason) {
    }

    public record SupplyFailure(String skuId, String reason) {
    }

    public record StockAdjust(String skuId, String direction, Integer qty, String reason) {
    }

    // ── 상품·카테고리·옵션 종류 ──
    public record ProductSku(String skuId, String name, List<String> values, Long defaultSupplyPrice, Boolean onSale) {
    }

    public record NoticeValue(Boolean custom, String itemKey, String value, Boolean unavailable, String unavailableReason, String name, Integer sortOrder) {
    }

    public record Notice(String categoryCode, String title, List<String> skuNames, List<NoticeValue> values) {
    }

    public record Product(String categoryId, String name, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                          String normalPriceSource, String withdrawalRestriction, List<String> optionTypeCodes, List<ProductSku> skus, List<String> images,
                          List<Notice> notices, Integer version) {
    }

    public record Category(String id, String tempKey, String parent, String name, Boolean hidden) {
    }

    public record OptionType(String code, String name, Boolean hidden) {
    }

    // ── 주문 ──
    public record OrderIds(List<String> orderIds) {
    }

    public record ShipRow(String orderId, String carrierCode, String trackingNo) {
    }

    public record Ship(List<ShipRow> rows) {
    }

    public record Memo(String memo) {
    }

    public record Tracking(String carrierCode, String trackingNo, String reason) {
    }

    public record RefundLine(String itemId, Integer qty) {
    }

    public record Refund(List<RefundLine> lines, Long amount, Boolean businessFault, String reason) {
    }

    public record Export(String groupBuyId, String reason) {
    }

    // ── 요청 ──
    public record Approve(Long expectedAmount) {
    }

    public record Reject(String reasonCode, String reason, List<String> photoIds) {
    }

    public record Pickup(String carrierCode, String trackingNo) {
    }

    public record Receive(Instant receivedAt) {
    }

    public record Disposition(String requestItemId, String disposition, Integer qty) {
    }

    public record Complete(List<Disposition> dispositions, Long compensationAmount, Long expectedAmount) {
    }

    public record ExchangeShip(String carrierCode, String trackingNo) {
    }

    // ── 설정 ──
    public record ShippingPolicy(String code, String name, Long baseFee, Long freeShippingOver, Long returnFee, String returnFeeMode, Instant effectiveFrom) {
    }

    public record Carrier(String code, String name, String trackingUrlTemplate, Boolean active) {
    }

    public record Carriers(List<Carrier> items) {
    }

    public record BusinessValues(String companyName, String representative, String businessNo, String mailOrderNo, String mailOrderAuthority, String address, String phone,
                                 String email, String businessHours, String privacyOfficer, String hostingProvider, String shippingRefundPolicy) {
    }

    public record BusinessInfo(BusinessValues values) {
    }

    public record Checklist(Boolean checked) {
    }

    public record SalesToggle(Boolean enabled, String reason) {
    }

    public record SettingsValues(Long holdMinutes, Long thresholdPeriodMaxDays, Long firstComePeriodMaxDays, Long extensionMaxMinutes, Long extensionWindowMinutes,
                                 Long judgeGraceMinutes, Long compensationCap, Long largeRefundThreshold, Long addressRevealMinutes, Long expiredAddressRetentionDays,
                                 Long shipWithinBusinessDays, Long supplyChoiceBusinessDays, List<LocalDate> holidays) {
    }

    public record Settings(SettingsValues values) {
    }
}
