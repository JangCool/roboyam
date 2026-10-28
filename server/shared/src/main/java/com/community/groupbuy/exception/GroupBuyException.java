package com.community.groupbuy.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 공구 업무 오류(계약 「감사·오류 코드 후보」) — 화면이 분기하는 값은 {@link #getErrorCode()}, 문구는 번들 {@code error.<코드>}.
 * 종류가 많아 클래스 하나에 만드는 길(정적 메서드)을 모았다. 추가 값은 오류 본문 최상위 칸이다({@code temporary}·{@code skuId}·{@code amount} 등).
 */
public class GroupBuyException extends CommonException {

    public static final String NOT_FOUND = "GROUP_BUY_NOT_FOUND";
    public static final String ORDER_NOT_FOUND = "GROUP_BUY_ORDER_NOT_FOUND";
    public static final String NOT_RECRUITING = "GROUP_BUY_NOT_RECRUITING";
    public static final String SOLD_OUT = "GROUP_BUY_SOLD_OUT";
    public static final String SKU_SOLD_OUT = "GROUP_BUY_SKU_SOLD_OUT";
    public static final String SKU_NOT_ON_SALE = "GROUP_BUY_SKU_NOT_ON_SALE";
    public static final String ADDON_ONLY = "GROUP_BUY_ADDON_ONLY";
    public static final String LIMIT_EXCEEDED = "GROUP_BUY_LIMIT_EXCEEDED";
    public static final String ALREADY_JOINED = "GROUP_BUY_ALREADY_JOINED";
    public static final String SALES_CLOSED = "GROUP_BUY_SALES_CLOSED";
    public static final String HOLD_EXPIRED = "GROUP_BUY_HOLD_EXPIRED";
    public static final String LOCKED_FIELD = "GROUP_BUY_LOCKED_FIELD";
    public static final String QTY_BELOW_FLOOR = "GROUP_BUY_QTY_BELOW_FLOOR";
    public static final String ROUND_EXISTS = "GROUP_BUY_ROUND_EXISTS";
    public static final String ROUND_SOURCE_INVALID = "GROUP_BUY_ROUND_SOURCE_INVALID";
    public static final String AMOUNT_CHANGED = "GROUP_BUY_AMOUNT_CHANGED";
    public static final String CANCEL_NOT_ALLOWED = "GROUP_BUY_CANCEL_NOT_ALLOWED";
    public static final String INVALID_STATE = "GROUP_BUY_INVALID_STATE";
    public static final String VERSION_CONFLICT = "GROUP_BUY_VERSION_CONFLICT";
    public static final String SKU_UNLIMITED = "GROUP_BUY_SKU_UNLIMITED";
    public static final String NOTICE_INCOMPLETE = "GROUP_BUY_NOTICE_INCOMPLETE";
    public static final String SUPPLY_PRICE_MISSING = "GROUP_BUY_SUPPLY_PRICE_MISSING";
    public static final String NEGATIVE_MARGIN = "GROUP_BUY_NEGATIVE_MARGIN";
    public static final String PRODUCT_NOT_FOUND = "GROUP_BUY_PRODUCT_NOT_FOUND";
    public static final String PRODUCT_SKU_IN_USE = "GROUP_BUY_PRODUCT_SKU_IN_USE";
    public static final String OPTION_TYPE_IN_USE = "GROUP_BUY_OPTION_TYPE_IN_USE";
    // 2b-3 발송·요청·환불
    public static final String REQUEST_NOT_FOUND = "GROUP_BUY_REQUEST_NOT_FOUND";
    public static final String REQUEST_OPEN = "GROUP_BUY_REQUEST_OPEN";
    public static final String REQUEST_NOT_OPEN = "GROUP_BUY_REQUEST_NOT_OPEN";
    public static final String RETURN_WINDOW_CLOSED = "GROUP_BUY_RETURN_WINDOW_CLOSED";
    public static final String NOT_RESELLABLE = "GROUP_BUY_NOT_RESELLABLE";
    public static final String REFUND_AMOUNT_CHANGED = "GROUP_BUY_REFUND_AMOUNT_CHANGED";
    public static final String REJECT_REASON_NOT_ALLOWED = "GROUP_BUY_REJECT_REASON_NOT_ALLOWED";
    public static final String PAYMENT_UNRECONCILED = "GROUP_BUY_PAYMENT_UNRECONCILED";
    public static final String REFUND_SHIPPED = "GROUP_BUY_REFUND_SHIPPED";
    public static final String REFUND_LARGE_REQUIRED = "GROUP_BUY_REFUND_LARGE_REQUIRED";
    public static final String ORDER_STATE = "GROUP_BUY_ORDER_STATE";
    public static final String ORDER_NOT_PREPARABLE = "GROUP_BUY_ORDER_NOT_PREPARABLE";
    public static final String ORDER_NOT_SHIPPABLE = "GROUP_BUY_ORDER_NOT_SHIPPABLE";
    public static final String NOT_ORDERABLE = "GROUP_BUY_NOT_ORDERABLE";
    public static final String TRACKING_INVALID = "GROUP_BUY_TRACKING_INVALID";
    public static final String ADDRESS_LOCKED = "GROUP_BUY_ADDRESS_LOCKED";
    public static final String ADDRESS_ERASED = "GROUP_BUY_ADDRESS_ERASED";
    public static final String EXPORT_NOT_READY = "GROUP_BUY_EXPORT_NOT_READY";
    public static final String EXCHANGE_NOT_ALLOWED = "GROUP_BUY_EXCHANGE_NOT_ALLOWED";
    public static final String SUPPLY_CHOICE_CLOSED = "GROUP_BUY_SUPPLY_CHOICE_CLOSED";
    public static final String COMPENSATION_OVER_CAP = "GROUP_BUY_COMPENSATION_OVER_CAP";
    public static final String BANK_TRANSFER_STATE = "COMMERCE_BANK_TRANSFER_STATE";
    public static final String CARRIER_IN_USE = "COMMERCE_CARRIER_IN_USE";
    public static final String SHIPPING_POLICY_NOT_FOUND = "SHIPPING_POLICY_NOT_FOUND";
    public static final String SHIPPING_POLICY_CONFLICT = "SHIPPING_POLICY_CONFLICT";
    // 2b-4 설정·관리자 API
    public static final String SHIPPING_POLICY_STARTED = "COMMERCE_SHIPPING_POLICY_STARTED";
    public static final String CATEGORY_IN_USE = "GROUP_BUY_CATEGORY_IN_USE";

    private GroupBuyException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    private GroupBuyException with(String name, Object value) {
        extra(name, value);
        return this;
    }

    public static GroupBuyException notFound() {
        return new GroupBuyException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    public static GroupBuyException orderNotFound() {
        return new GroupBuyException(HttpStatus.NOT_FOUND, ORDER_NOT_FOUND);
    }

    public static GroupBuyException notRecruiting() {
        return new GroupBuyException(HttpStatus.CONFLICT, NOT_RECRUITING);
    }

    /** @param temporary 잠시 품절(확정 + 선점 = 최대 — 선점이 풀리면 다시 살 수 있다)인가, 다 팔림인가(결정 17) */
    public static GroupBuyException soldOut(boolean temporary) {
        return new GroupBuyException(HttpStatus.CONFLICT, SOLD_OUT).with("temporary", temporary);
    }

    public static GroupBuyException skuSoldOut(long skuId, boolean temporary) {
        return new GroupBuyException(HttpStatus.CONFLICT, SKU_SOLD_OUT).with("skuId", Long.toString(skuId)).with("temporary", temporary);
    }

    /** 판매하지 않는 단품(운영자가 끈 조합 — 결정 88) */
    public static GroupBuyException skuNotOnSale(long skuId) {
        return new GroupBuyException(HttpStatus.CONFLICT, SKU_NOT_ON_SALE).with("skuId", Long.toString(skuId));
    }

    /** 본 상품 줄을 모두 빼고 추가 구성상품만 남기려 했다 — 남은 추가상품도 함께 취소한다(결정 88). {@code skuIds} 는 남는 추가상품 */
    public static GroupBuyException addonOnly(java.util.List<String> skuIds) {
        return new GroupBuyException(HttpStatus.CONFLICT, ADDON_ONLY).with("skuIds", skuIds);
    }

    /** @param remaining 지금 더 살 수 있는 수량 */
    public static GroupBuyException limitExceeded(int remaining) {
        return new GroupBuyException(HttpStatus.CONFLICT, LIMIT_EXCEEDED).with("remaining", Math.max(0, remaining));
    }

    public static GroupBuyException alreadyJoined() {
        return new GroupBuyException(HttpStatus.CONFLICT, ALREADY_JOINED);
    }

    public static GroupBuyException salesClosed() {
        return new GroupBuyException(HttpStatus.FORBIDDEN, SALES_CLOSED);
    }

    public static GroupBuyException holdExpired() {
        return new GroupBuyException(HttpStatus.CONFLICT, HOLD_EXPIRED);
    }

    /** 모집 시작 뒤 가격·기간·옵션 구성·배송비·방식을 고치려 했다 — {@code field} 는 그 칸 */
    public static GroupBuyException lockedField(String field) {
        return new GroupBuyException(HttpStatus.CONFLICT, LOCKED_FIELD).with("field", field);
    }

    /** @param floor 줄일 수 있는 하한(최대가 이 값 아래로 못 간다) */
    public static GroupBuyException qtyBelowFloor(int floor) {
        return new GroupBuyException(HttpStatus.CONFLICT, QTY_BELOW_FLOOR).with("floor", floor);
    }

    public static GroupBuyException roundExists() {
        return new GroupBuyException(HttpStatus.CONFLICT, ROUND_EXISTS);
    }

    public static GroupBuyException roundSourceInvalid() {
        return new GroupBuyException(HttpStatus.CONFLICT, ROUND_SOURCE_INVALID);
    }

    /** 화면이 본 금액과 서버 계산이 다르다(결정 72) — {@code amount} 는 서버 계산 */
    public static GroupBuyException amountChanged(long amount) {
        return new GroupBuyException(HttpStatus.CONFLICT, AMOUNT_CHANGED).with("amount", amount);
    }

    /** 본인 즉시 취소가 안 되는 때 — 취소 요청(운영자 승인)으로 간다(10-3 표) */
    public static GroupBuyException cancelNotAllowed() {
        return new GroupBuyException(HttpStatus.CONFLICT, CANCEL_NOT_ALLOWED);
    }

    public static GroupBuyException invalidState() {
        return new GroupBuyException(HttpStatus.CONFLICT, INVALID_STATE);
    }

    public static GroupBuyException versionConflict() {
        return new GroupBuyException(HttpStatus.CONFLICT, VERSION_CONFLICT);
    }

    /** 한도 없는 단품에 단품 추가 입고·줄이기 — 공구 단위로 한다(결정 80) */
    public static GroupBuyException skuUnlimited() {
        return new GroupBuyException(HttpStatus.CONFLICT, SKU_UNLIMITED);
    }

    /**
     * 상품정보제공고시가 다 차지 않았다(결정 87) — {@code noNotice}(양식이 하나도 없음), {@code notices}(빈 항목이 있는 양식: 차례·제목·품목·빈 항목 키),
     * {@code unassignedSkus}(속할 양식이 없는 단품 이름 — 기본 양식이 없는 MAIN, 자기 양식이 없는 ADDON).
     */
    public static GroupBuyException noticeIncomplete(boolean noNotice, java.util.List<java.util.Map<String, Object>> notices, java.util.List<String> unassignedSkus) {
        return noticeIncomplete(null, noNotice, notices, unassignedSkus);
    }

    /** @param productId 고시가 덜 된 상품(공구 상품 또는 추가 구성상품의 상품 — 결정 89) */
    public static GroupBuyException noticeIncomplete(Long productId, boolean noNotice, java.util.List<java.util.Map<String, Object>> notices,
                                                     java.util.List<String> unassignedSkus) {
        return new GroupBuyException(HttpStatus.CONFLICT, NOTICE_INCOMPLETE).with("productId", productId == null ? "" : productId.toString()).with("noNotice", noNotice).with("notices", notices)
                .with("unassignedSkus", unassignedSkus);
    }

    /** 모집 시작 때 공급가가 빈 단품(결정 89 — 0 은 명시 입력만) */
    public static GroupBuyException supplyPriceMissing(java.util.List<String> skuIds) {
        return new GroupBuyException(HttpStatus.CONFLICT, SUPPLY_PRICE_MISSING).with("skuIds", skuIds);
    }

    /** 판매가 < 공급가(역마진) — 확인({@code marginConfirmed})해야 진행(결정 89) */
    public static GroupBuyException negativeMargin(java.util.List<String> skuIds) {
        return new GroupBuyException(HttpStatus.CONFLICT, NEGATIVE_MARGIN).with("skuIds", skuIds);
    }

    public static GroupBuyException productNotFound() {
        return new GroupBuyException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND);
    }

    /** 공구 단품이 가리키는 상품 단품은 지울 수 없다(상품 옵션 구성 바꾸기 — 결정 89) */
    public static GroupBuyException productSkuInUse(java.util.List<String> names) {
        return new GroupBuyException(HttpStatus.CONFLICT, PRODUCT_SKU_IN_USE).with("skuNames", names);
    }

    /** 쓰는 상품이 있는 옵션 종류는 지울 수 없다(숨기기만) */
    public static GroupBuyException optionTypeInUse(java.util.List<String> codes) {
        return new GroupBuyException(HttpStatus.CONFLICT, OPTION_TYPE_IN_USE).with("codes", codes);
    }

    public static GroupBuyException shippingPolicyNotFound() {
        return new GroupBuyException(HttpStatus.CONFLICT, SHIPPING_POLICY_NOT_FOUND);
    }

    /** 배송비 정책 예약이 기존 줄과 겹치거나 이미 시작된 시각이다 */
    public static GroupBuyException shippingPolicyConflict() {
        return new GroupBuyException(HttpStatus.CONFLICT, SHIPPING_POLICY_CONFLICT);
    }
    // ── 2b-3 발송·요청·환불 ────────────────────────────────────────────────

    /** 상품·아래 단이 있는 카테고리는 지울 수 없다(2b-4) — {@code name} */
    public static GroupBuyException categoryInUse(String name) {
        return new GroupBuyException(HttpStatus.CONFLICT, CATEGORY_IN_USE).with("name", name);
    }

    /** 적용이 시작된 배송비 정책 줄은 지울 수 없다(2b-4) */
    public static GroupBuyException shippingPolicyStarted() {
        return new GroupBuyException(HttpStatus.CONFLICT, SHIPPING_POLICY_STARTED);
    }

    public static GroupBuyException requestNotFound() {
        return new GroupBuyException(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND);
    }

    /** 주문에 열린 요청이 이미 있다(결정 44·66) — 그 요청이 끝나야 새 요청·발송·운영 환불 */
    public static GroupBuyException requestOpen() {
        return new GroupBuyException(HttpStatus.CONFLICT, REQUEST_OPEN);
    }

    /** 요청이 이 처리를 할 상태가 아니다(이미 처리·철회됨) */
    public static GroupBuyException requestNotOpen() {
        return new GroupBuyException(HttpStatus.CONFLICT, REQUEST_NOT_OPEN);
    }

    /** 반품 기한 지남(단순 변심 배송완료 7일 · 하자 3개월 — L2) */
    public static GroupBuyException returnWindowClosed() {
        return new GroupBuyException(HttpStatus.CONFLICT, RETURN_WINDOW_CLOSED);
    }

    /** 다시 판매할 수 없다 — 공구가 모집 중이 아니거나 단순 변심 줄이 아니다(결정 15) */
    public static GroupBuyException notResellable() {
        return new GroupBuyException(HttpStatus.CONFLICT, NOT_RESELLABLE);
    }

    /** 미리 본 금액과 지금 계산이 다르다 — 두 값을 보이고 확인 뒤 다시(결정 72) */
    public static GroupBuyException refundAmountChanged(long preview, long current) {
        return new GroupBuyException(HttpStatus.CONFLICT, REFUND_AMOUNT_CHANGED).with("previewAmount", preview).with("amount", current);
    }

    /** 이 요청에 쓸 수 없는 거절 사유(결정 55 — 청약철회 제한 안내 없는 가치 감소, 반품에 「이미 발송함」 등) 또는 근거 사진 없음 */
    public static GroupBuyException rejectReasonNotAllowed() {
        return new GroupBuyException(HttpStatus.CONFLICT, REJECT_REASON_NOT_ALLOWED);
    }

    /** 그 결제에 열린 대사 이슈(콘솔 직접 취소)가 있다 — 대사 처리 뒤(결정 73) */
    public static GroupBuyException paymentUnreconciled() {
        return new GroupBuyException(HttpStatus.CONFLICT, PAYMENT_UNRECONCILED);
    }

    /** 배송 중 주문은 운영 환불을 받지 않는다 — 배송완료 뒤 또는 반품 요청으로(결정 32) */
    public static GroupBuyException refundShipped() {
        return new GroupBuyException(HttpStatus.CONFLICT, REFUND_SHIPPED);
    }

    /** 운영자가 금액을 정한 환불 누계가 고액 문턱 이상 — 고액 환불 권한이 필요하다(결정 70) */
    public static GroupBuyException refundLargeRequired(long threshold) {
        return new GroupBuyException(HttpStatus.FORBIDDEN, REFUND_LARGE_REQUIRED).with("threshold", threshold);
    }

    /** 주문이 이 처리를 할 상태가 아니다 */
    public static GroupBuyException orderState() {
        return new GroupBuyException(HttpStatus.CONFLICT, ORDER_STATE);
    }

    public static GroupBuyException orderNotPreparable() {
        return new GroupBuyException(HttpStatus.CONFLICT, ORDER_NOT_PREPARABLE);
    }

    /** 발송할 수 없다 — 상태·발주 전·열린 요청·공급 불가 답 기다림 */
    public static GroupBuyException orderNotShippable() {
        return new GroupBuyException(HttpStatus.CONFLICT, ORDER_NOT_SHIPPABLE);
    }

    /** 발주할 수 없다(성사된 마감 후 발주 공구만) */
    public static GroupBuyException notOrderable() {
        return new GroupBuyException(HttpStatus.CONFLICT, NOT_ORDERABLE);
    }

    /** 택배사가 없거나 꺼졌다, 송장이 숫자·영문 6~30자가 아니다 */
    public static GroupBuyException trackingInvalid() {
        return new GroupBuyException(HttpStatus.BAD_REQUEST, TRACKING_INVALID);
    }

    /** 배송지를 고칠 수 있는 기한이 지났다(결정 30) */
    public static GroupBuyException addressLocked() {
        return new GroupBuyException(HttpStatus.CONFLICT, ADDRESS_LOCKED);
    }

    /** 보관 기간이 지나 배송지를 지웠다(10-9) */
    public static GroupBuyException addressErased() {
        return new GroupBuyException(HttpStatus.CONFLICT, ADDRESS_ERASED);
    }

    /** 명단을 내보낼 때가 아니다(마감 후 발주 공구는 발주 뒤 — 결정 30) */
    public static GroupBuyException exportNotReady() {
        return new GroupBuyException(HttpStatus.CONFLICT, EXPORT_NOT_READY);
    }

    /** 교환은 하자·오배송·훼손만(결정 54) */
    public static GroupBuyException exchangeNotAllowed() {
        return new GroupBuyException(HttpStatus.CONFLICT, EXCHANGE_NOT_ALLOWED);
    }

    /** 공급 불가 선택 기한이 지났거나 이미 답했다 */
    public static GroupBuyException supplyChoiceClosed() {
        return new GroupBuyException(HttpStatus.CONFLICT, SUPPLY_CHOICE_CLOSED);
    }

    /** 보상액이 설정 상한을 넘는다(10-8 — 기본 10,000원) */
    public static GroupBuyException compensationOverCap(long cap) {
        return new GroupBuyException(HttpStatus.CONFLICT, COMPENSATION_OVER_CAP).with("cap", cap);
    }

    /** 계좌 송금이 이 처리를 할 상태가 아니다 */
    public static GroupBuyException bankTransferState() {
        return new GroupBuyException(HttpStatus.CONFLICT, BANK_TRANSFER_STATE);
    }

    /** 발송 이력이 있는 택배사는 지우지 못하고 끄기만(결정 11) */
    public static GroupBuyException carrierInUse(java.util.List<String> codes) {
        return new GroupBuyException(HttpStatus.CONFLICT, CARRIER_IN_USE).with("codes", codes);
    }
}
