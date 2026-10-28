package com.community.groupbuy.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 내 공구 주문 화면의 응답(user-web {@code useGroupBuyOrderApi.types.ts} 와 같은 모양). 환불 예정 금액·회수 방식별 결과·단추 상태는 서버 값({@code GroupBuyRefundCalculator} 한 곳 — 결정 72).
 * 배송지는 가린 값이 기본이고 「전체 보기」는 따로 받는다(10-9).
 */
public final class MyGroupBuyOrderView {

    private MyGroupBuyOrderView() {
    }

    public record Summary(String orderNo, String groupBuyId, String title, String thumbnailUrl, String status, String firstItemName, int itemCount, long amount,
                          long refundedAmount, Instant createdAt, String attention) {
    }

    public record Page(List<Summary> items, int page, int size, long total) {
    }

    public record Item(String id, String skuId, String skuName, String kind, int qty, long unitPrice, long itemAmount, int canceledQty, int returnedQty,
                       int selectableQty) {
    }

    public record Address(String recipient, String phone, String postcode, String address1, String address2, String memo, boolean masked, boolean purged) {
    }

    public record Shipment(String carrierName, String trackingNo, String trackingUrl, String kind, Instant shippedAt, Instant deliveredAt) {
    }

    public record RequestItem(String skuId, String skuName, String kind, int qty, String reason) {
    }

    public record RequestActions(boolean withdraw, boolean payReturnFee) {
    }

    public record Request(String id, String type, String status, List<RequestItem> items, String collectionMethod, String resolution, Long previewAmount,
                          Long refundAmount, Long returnFeeDue, String rejectReasonCode, String pickupTrackingNo, Instant createdAt, Instant handledAt,
                          RequestActions actions) {
    }

    public record Refund(String id, String cause, long amount, String progress, String method, Instant requestedAt, Instant completedAt) {
    }

    public record SupplyChoice(String skuName, String reason, Instant deadline, String choice, boolean defaulted, long cancelAllRefund) {
    }

    public record DelayNotice(LocalDate previousPlannedAt, LocalDate newPlannedAt, String reason, Instant noticedAt, long refundAmount) {
    }

    public record Actions(boolean payAgain, boolean revealAddress, boolean editAddress, boolean cancelNow, boolean requestCancel, boolean requestReturn,
                          boolean delayCancel, boolean supplyChoice) {
    }

    public record GroupBuyRef(String id, String title, String thumbnailUrl, String status, String fulfillment, String closeReason, int roundNo) {
    }

    public record Detail(String orderNo, String status, GroupBuyRef groupBuy, List<Item> items, long itemsAmount, long shippingFee, long amount, long refundedAmount,
                         long compensationAmount, Instant createdAt, Instant paidAt, Instant reservedUntil, LocalDate shipPlannedAt, Integer shipWithinBusinessDays,
                         Address address, String addressEditableUntilText, Shipment shipment, DelayNotice delayNotice, SupplyChoice supplyChoice,
                         List<Request> requests, List<Refund> refunds, Instant returnUntil, Instant defectReturnUntil, Actions actions) {
    }

    public record CancelPreview(long refundAmount, long itemRefund, long shippingRefund, long reshippingFee) {
    }

    public record ReturnOutcome(long refundAmount, long itemRefund, long shippingRefund, long returnFee, long reshippingFee, boolean compensationEligible) {
    }

    public record ReturnPreview(String feeBearer, Map<String, ReturnOutcome> byMethod, String returnAddress) {
    }

    public record RequestCreated(Request request, GroupBuyMemberView.PaymentStart returnFeePayment) {
    }

    public record Photo(String id, String url) {
    }
}
