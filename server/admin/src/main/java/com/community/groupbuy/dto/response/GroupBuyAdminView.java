package com.community.groupbuy.dto.response;

import com.community.payment.dto.response.PaymentMemberView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 관리자 공구·수량·상품 화면의 응답(admin-web {@code useGroupBuyAdminApi.types.ts}·{@code useGroupBuyProductApi.types.ts} 와 같은 모양). ID 는 글자.
 * 공급가는 {@code commerce.product.price}·{@code commerce.settlement.read} 를 가진 운영자에게만 싣는다(없으면 null — 결정 89).
 */
public final class GroupBuyAdminView {

    private GroupBuyAdminView() {
    }

    public record Page<T>(List<T> items, long total) {
    }

    // ── 공구 목록 ──
    public record Row(String id, String title, String productName, int roundNo, String roundRootId, String status, String closeReason, String fulfillment, int minQty,
                      int maxQty, int currentQty, int reservedQty, long groupPrice, Instant startAt, Instant endAt, Instant extendedUntil, String thumbnailUrl, int orderCount,
                      Instant updatedAt) {
    }

    public record ListResponse(List<Row> items, long total, Map<String, Long> counts) {
    }

    // ── 상세 ──
    public record Image(String mediaId, String url, String thumbUrl) {
    }

    public record ImageUpload(String mediaId, String url, String thumbUrl, int width, int height) {
    }

    public record OptionValue(String id, String name, int sortOrder) {
    }

    public record OptionAxis(String id, String name, int sortOrder, List<OptionValue> values) {
    }

    public record Sku(String skuId, String kind, List<String> valueIds, String name, long extraPrice, long unitPrice, Integer maxQty, Integer initialMaxQty, int currentQty,
                      int reservedQty, boolean onSale, String imageKey, String imageUrl, int sortOrder, String productSkuId, Long supplyPrice) {
    }

    public record ShippingSnapshot(long shippingFee, Long freeShippingOver, long returnShippingFee, String returnFeeMode) {
    }

    public record RoundRef(String id, int roundNo, String status, String title) {
    }

    public record DelayNotice(String id, LocalDate previousPlannedAt, LocalDate newPlannedAt, String reason, int notifiedOrders, String operator, Instant createdAt) {
    }

    public record SupplyFailure(String id, String skuId, String skuName, String skuKind, String reason, int refundedLines, long refundAmount, int choiceOrders,
                                int choicePending, Instant choiceDeadline, String operator, Instant createdAt) {
    }

    public record Actions(boolean canStart, boolean canCloseEarly, boolean canCancel, boolean canMarkOrdered, boolean canCreateRound, String roundBlockedReason,
                          boolean canDelayNotice, boolean canSupplyFailure, boolean canAdjustStock) {
    }

    public record NoticeValue(String itemKey, String value, boolean unavailable, String unavailableReason) {
    }

    public record NoticeCustomItem(String name, String value, int sortOrder) {
    }

    public record Notice(String categoryCode, String title, List<String> skuIds, List<String> skuNames, List<NoticeValue> values, List<NoticeCustomItem> customItems) {
    }

    public record Detail(String id, String status, String closeReason, int roundNo, String roundRootId, List<RoundRef> rounds, String title, String productId,
                         String productName, String categoryPath, String summary, String spec, String description, List<Image> images, long normalPrice, long groupPrice,
                         String normalPriceBasis, String normalPriceSource, List<OptionAxis> axes, List<Sku> skus, int minQty, String fulfillment, int maxQty,
                         Integer initialMaxQty, int addedQty, int currentQty, int reservedQty, Integer maxQtyPerUser, boolean roundLimitShared, Instant startAt, Instant endAt,
                         String extensionMode, Instant extendedUntil, Instant closingAt, String shippingPolicyCode, ShippingSnapshot shippingSnapshot,
                         LocalDate shipPlannedAt, Integer shipWithinBusinessDays, String withdrawalRestriction, int preShipOrderCount, int version, int revisionNo,
                         List<Notice> notices, Actions actions, List<DelayNotice> delayNotices, List<SupplyFailure> supplyFailures, String createdBy, Instant createdAt,
                         Instant updatedAt) {
    }

    public record PolicyLine(long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode, Instant effectiveFrom) {
    }

    public record PolicyChoice(String code, String name, PolicyLine current, PolicyLine upcoming) {
    }

    public record PeriodMaxDays(int threshold, int firstCome) {
    }

    public record FormOptions(List<PolicyChoice> shippingPolicies, int defaultShipWithinBusinessDays, PeriodMaxDays periodMaxDays) {
    }

    public record SkuMax(String name, String kind, Integer maxQty) {
    }

    public record Revision(int revisionNo, String kind, List<String> changedFields, String title, String summary, String description, int imageCount, int maxQty,
                           int addedQty, List<SkuMax> skuMax, LocalDate shipPlannedAt, List<Notice> notices, String operator, Instant createdAt) {
    }

    public record CancelPreview(int refundOrders, long refundAmount, int expireOrders, int shippedOrders) {
    }

    public record SupplyFailurePreview(String skuId, String skuName, String skuKind, int cancelLines, long refundAmount, int fullRefundOrders, int choiceOrders,
                                       Instant choiceDeadline) {
    }

    public record RoundCreated(String id, int roundNo) {
    }

    // ── 수량 ──
    public record StockLine(String skuId, String kind, String name, boolean onSale, Integer maxQty, Integer initialMaxQty, int addedQty, int currentQty, int reservedQty,
                            int remainingQty, int returnedQty, int restockedQty, boolean soldOut, boolean tempSoldOut, int reduceFloor) {
    }

    public record Hold(String orderNo, PaymentMemberView member, String lines, int qty, Instant reservedUntil) {
    }

    public record StockGroupBuy(String id, String title, int roundNo, String status, int minQty, String fulfillment) {
    }

    public record Stock(StockGroupBuy groupBuy, StockLine total, List<StockLine> skus, List<Hold> holds, boolean canAdjust) {
    }

    public record Movement(String id, String kind, String skuName, String skuKind, int qty, int currentAfter, int reservedAfter, int maxAfter, Integer skuCurrentAfter,
                           Integer skuReservedAfter, Integer skuMaxAfter, String orderNo, String actor, String reason, Instant createdAt) {
    }

    // ── 상품·카테고리·옵션 종류 ──
    public record Category(String id, String parentId, String name, int sortOrder, boolean hidden, int productCount) {
    }

    public record OptionType(String code, String name, int sortOrder, boolean hidden, int productCount, List<String> usedValues) {
    }

    public record ProductSku(String skuId, List<String> values, List<String> valueIds, String name, Long defaultSupplyPrice, boolean onSale) {
    }

    public record ProductUse(String id, String title, String status, int roundNo) {
    }

    public record ProductRow(String id, String name, String categoryId, String categoryPath, String thumbUrl, int skuCount, int groupBuyCount, int recruitingCount,
                             Instant updatedAt) {
    }

    public record ProductDetail(String id, String name, String categoryId, String categoryPath, String summary, String spec, String description, List<Image> images,
                                long normalPrice, String normalPriceBasis, String normalPriceSource, String withdrawalRestriction, List<String> optionTypeCodes,
                                List<String> optionNames, List<OptionAxis> axes, List<ProductSku> skus, List<Notice> notices, boolean structureLocked,
                                List<ProductUse> groupBuys, int version, Instant updatedAt) {
    }

    public record AddonSource(String productSkuId, String productId, String productName, String name, String imageUrl, Long defaultSupplyPrice) {
    }
}
