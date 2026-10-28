package com.community.groupbuy.dto.response;

import com.community.payment.dto.response.PaymentCheckoutView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 공구 회원 화면의 응답(user-web {@code useGroupBuyApi.types.ts} 와 같은 모양 — 계약 「2b-1 화면 목업 › 백엔드에 필요한 API 후보」·결정 88·89).
 * ID 는 모두 글자(스노플레이크 — JS 숫자 정밀도). 금액·남은 수량·품절·단추 상태는 모두 서버 판정이다. 공급가는 싣지 않는다(결정 89).
 */
public final class GroupBuyMemberView {

    private GroupBuyMemberView() {
    }

    /** 목록 한 줄 */
    public record ListItem(String id, String title, String thumbnailUrl, String status, String phase, String closeReason, String fulfillment, long groupPrice,
                           boolean priceFrom, Long normalPrice, int minQty, int maxQty, int currentQty, int remainingQty, String stockState, Instant startAt,
                           Instant endAt, Instant extendedUntil, boolean closingSoon, int roundNo, String categoryId) {
    }

    public record ListPage(List<ListItem> items, int page, int size, long total) {
    }

    public record CategoryNode(String id, String name, List<Child> children) {

        public record Child(String id, String name) {
        }
    }

    public record Image(String url, String alt) {
    }

    public record Shipping(long fee, Long freeShippingOver, long returnFee, String returnFeeMode) {
    }

    public record DelayNotice(LocalDate previousPlannedAt, LocalDate newPlannedAt, String reason, Instant noticedAt) {
    }

    public record AxisValue(String valueId, String value) {
    }

    public record Axis(String axisId, String name, List<AxisValue> values) {
    }

    public record Sku(String skuId, String kind, String name, List<String> valueIds, long unitPrice, Integer remaining, boolean soldOut, boolean temporarilyUnavailable,
                      boolean onSale) {
    }

    public record Availability(String groupBuyId, int remaining, boolean soldOut, boolean temporarilyUnavailable, int addedQty, List<Axis> axes, List<Sku> skus) {
    }

    public record SkuWatches(List<String> watchable, List<String> watching) {
    }

    public record NoticeRow(String itemKey, String no, String group, String name, String description, String kind, String value, boolean unavailable,
                            String unavailableReason) {
    }

    public record NoticeCustomItem(String name, String value, int sortOrder) {
    }

    public record Notice(String categoryCode, int categoryNo, String categoryName, String title, List<String> skuIds, List<String> skuNames, List<NoticeRow> rows,
                         List<NoticeCustomItem> customItems, String catalogVersion, String noticeNo) {
    }

    public record Purchase(String reason) {
    }

    public record Actions(boolean order, boolean watchRestock, boolean watchClosingSoon, boolean watchNextRound) {
    }

    /** 상세 — 목록 줄의 칸 + 아래 */
    public record Detail(String id, String title, String thumbnailUrl, String status, String phase, String closeReason, String fulfillment, long groupPrice,
                         boolean priceFrom, Long normalPrice, int minQty, int maxQty, int currentQty, int remainingQty, String stockState, Instant startAt, Instant endAt,
                         Instant extendedUntil, boolean closingSoon, int roundNo, String categoryId, List<Image> images, String descriptionHtml, String summary, String spec,
                         String normalPriceBasis, LocalDate shipPlannedAt, Integer shipWithinBusinessDays, Shipping shipping, String withdrawalRestriction,
                         String extensionMode, int extensionMaxMinutes, int addedQty, Integer maxQtyPerUser, boolean roundLimitShared, int holdMinutes,
                         Availability availability, SkuWatches skuWatches, List<Notice> notices, DelayNotice delayNotice, Purchase purchase, Actions actions,
                         Map<String, Boolean> watches, String myOrderNo) {
    }

    public record QuoteItem(String skuId, String kind, String name, int qty, long unitPrice, long itemAmount) {
    }

    public record Quote(List<QuoteItem> items, long itemsAmount, long shippingFee, long totalAmount, Long freeShippingRemaining) {
    }

    /** 포트원 구매자(KG이니시스 — 가짜 게이트웨이면 null) */
    public record Customer(String fullName, String phoneNumber, String email) {
    }

    public record PaymentStart(String orderType, String orderRefId, Instant expiresAt, PaymentCheckoutView checkout, String orderNo, Customer customer) {
    }

    public record OrderCreated(String orderNo, long amount, Instant reservedUntil, PaymentStart payment) {
    }
}
