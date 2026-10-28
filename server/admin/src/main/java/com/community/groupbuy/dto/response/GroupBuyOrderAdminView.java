package com.community.groupbuy.dto.response;

import com.community.payment.dto.response.PaymentMemberView;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 관리자 공구 주문·요청 화면의 응답(admin-web {@code useGroupBuyOrderApi.types.ts}·{@code useGroupBuyRequestApi.types.ts} 와 같은 모양). 배송지는 가린 값만 —
 * 평문은 열람({@code address-reveal}) 응답에만 실린다(10-9).
 */
public final class GroupBuyOrderAdminView {

    private GroupBuyOrderAdminView() {
    }

    public record GroupBuyRef(String id, String title, int roundNo, String status, String fulfillment) {
    }

    public record OpenRequest(String id, String type) {
    }

    public record Row(String id, String orderNo, GroupBuyRef groupBuy, PaymentMemberView member, String itemSummary, int totalQty, long amount, long refundedAmount,
                      String status, OpenRequest openRequest, boolean supplyChoicePending, boolean delayCancelable, String carrierCode, String trackingNo, Instant createdAt,
                      Instant paidAt, Instant shippedAt, Instant deliveredAt) {
    }

    public record ListResponse(List<Row> items, long total, Map<String, Long> counts) {
    }

    public record Item(String id, String skuId, String skuName, String kind, int qty, long unitPrice, long itemAmount, int canceledQty, int returnedQty, int restockedQty) {
    }

    public record Amounts(long itemsAmount, long shippingFee, long amount, long cashAmount, long pointAmount, long refundedAmount, long compensationAmount,
                          long reshippingFeeCharged, long remaining) {
    }

    public record Payment(String paymentOrderId, String paymentId, String status, String payMethod, boolean escrow, Instant paidAt) {
    }

    public record MaskedAddress(String recipient, String phone, String postcode, String address, String memo, Instant erasedAt) {
    }

    public record RevealedAddress(String recipient, String phone, String postcode, String address1, String address2, String memo, Instant expiresAt) {
    }

    public record AddressView(String operator, String reason, Instant viewedAt) {
    }

    public record StatusChange(String from, String to, String actor, String note, Instant at) {
    }

    public record RefundRecord(String id, String cause, String method, String state, long itemRefund, long shippingRefund, long returnFee, long reshippingFee,
                               long reshippingRefund, long compensation, long total, boolean businessFault, String reason, String operator, String requestId, Instant requestedAt,
                               Instant completedAt) {
    }

    public record Shipment(String kind, String carrierCode, String carrierName, String trackingNo, String trackingUrl, Instant shippedAt, Instant deliveredAt) {
    }

    public record RequestRef(String id, String type, String status, Instant createdAt) {
    }

    public record SupplyChoice(String choice, Instant deadline, boolean defaulted) {
    }

    public record Actions(boolean canPrepare, boolean canShip, boolean canFixTracking, boolean canDeliver, boolean canRevealAddress, String refundMode,
                          String refundBlockedReason) {
    }

    public record Detail(String id, String orderNo, GroupBuyRef groupBuy, PaymentMemberView member, String itemSummary, int totalQty, long amount, long refundedAmount,
                         String status, OpenRequest openRequest, boolean supplyChoicePending, boolean delayCancelable, String carrierCode, String trackingNo, Instant createdAt,
                         Instant paidAt, Instant shippedAt, Instant deliveredAt, List<Item> items, Amounts amounts, Payment payment, MaskedAddress address,
                         List<AddressView> addressViews, String adminMemo, List<StatusChange> history, List<RefundRecord> refunds, List<Shipment> shipments,
                         List<RequestRef> requests, SupplyChoice supplyChoice, Instant shipDueAt, long adminRefundTotal, long largeRefundThreshold, Actions actions) {
    }

    public record BulkRow(String orderId, String orderNo, boolean ok, String code, String message) {
    }

    public record Bulk(int succeeded, int failed, List<BulkRow> results) {
    }

    public record RefundPreview(long itemRefund, long shippingRefund, long reshippingFee, long total, long remainingAfter, long adminRefundTotalAfter,
                                long largeRefundThreshold, boolean needsLarge) {
    }

    public record RefundResult(String state, Detail order) {
    }

    public record ExportResult(String fileName, int rowCount, String downloadUrl) {
    }

    public record CarrierChoice(String code, String name) {
    }

    // ── 요청 ──
    public record ExtraPayment(String status, long amount, String paymentId, Instant paidAt, long refundedAmount) {
    }

    public record OrderRef(String id, String orderNo) {
    }

    public record RequestRow(String id, String type, String status, String resolution, OrderRef order, GroupBuyRef groupBuy, PaymentMemberView member, int lineCount,
                             int totalQty, String mainReason, String collectionMethod, long previewAmount, Instant createdAt, Instant receivedAt, int elapsedBusinessDays,
                             String deadlineLevel, ExtraPayment extraPayment) {
    }

    public record RequestList(List<RequestRow> items, long total, Map<String, Long> counts) {
    }

    public record DispositionQty(String disposition, int qty) {
    }

    public record RequestItem(String id, String orderItemId, String skuName, String kind, int qty, String reasonCode, String reasonText, long refundAmount,
                              List<DispositionQty> dispositions) {
    }

    public record Breakdown(long itemRefund, long shippingRefund, long returnFee, long reshippingFee, long reshippingRefund, long compensation, long total,
                            boolean operatorPaysReturnFee) {
    }

    public record Recalculated(Breakdown breakdown, List<String> reasons) {
    }

    public record Photo(String id, String url) {
    }

    public record Pickup(String carrierCode, String trackingNo) {
    }

    public record ExchangeShipment(String carrierCode, String trackingNo, Instant shippedAt) {
    }

    public record HistoryLine(String status, String actor, String note, Instant at) {
    }

    public record RequestActions(boolean canApprove, boolean canReject, boolean canSetPickup, boolean canReceive, boolean canComplete, boolean canExchangeShip,
                                 boolean canConvertToRefund, boolean resellAllowed) {
    }

    public record RequestDetail(String id, String type, String status, String resolution, OrderRef order, GroupBuyRef groupBuy, PaymentMemberView member, int lineCount,
                                int totalQty, String mainReason, String collectionMethod, long previewAmount, Instant createdAt, Instant receivedAt, int elapsedBusinessDays,
                                String deadlineLevel, ExtraPayment extraPayment, List<RequestItem> items, List<Photo> photos, Pickup pickup, Breakdown breakdown,
                                Recalculated recalculated, long compensationAmount, long compensationCap, boolean withdrawalRestricted, String rejectReasonCode,
                                String rejectReason, List<Photo> rejectPhotos, ExchangeShipment exchangeShipment, long adminRefundTotal, long largeRefundThreshold,
                                String handledBy, Instant handledAt, List<HistoryLine> history, RequestActions actions) {
    }
}
