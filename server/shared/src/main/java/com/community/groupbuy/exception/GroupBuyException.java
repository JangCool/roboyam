package com.community.groupbuy.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 공구 업무 오류(계약 「감사·오류 코드 후보」) — 화면이 분기하는 값은 {@link #getErrorCode()}, 문구는 번들 {@code error.<코드>}.
 * 종류가 많아 클래스 하나에 만드는 길(정적 메서드)을 모았다. 추가 값은 오류 본문 최상위 칸이다({@code temporary}·{@code optionId}·{@code amount} 등).
 */
public class GroupBuyException extends CommonException {

    public static final String NOT_FOUND = "GROUP_BUY_NOT_FOUND";
    public static final String ORDER_NOT_FOUND = "GROUP_BUY_ORDER_NOT_FOUND";
    public static final String NOT_RECRUITING = "GROUP_BUY_NOT_RECRUITING";
    public static final String SOLD_OUT = "GROUP_BUY_SOLD_OUT";
    public static final String OPTION_SOLD_OUT = "GROUP_BUY_OPTION_SOLD_OUT";
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
    public static final String OPTION_UNLIMITED = "GROUP_BUY_OPTION_UNLIMITED";
    public static final String NOTICE_INCOMPLETE = "GROUP_BUY_NOTICE_INCOMPLETE";
    public static final String SHIPPING_POLICY_NOT_FOUND = "SHIPPING_POLICY_NOT_FOUND";
    public static final String SHIPPING_POLICY_CONFLICT = "SHIPPING_POLICY_CONFLICT";

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

    public static GroupBuyException optionSoldOut(long optionId, boolean temporary) {
        return new GroupBuyException(HttpStatus.CONFLICT, OPTION_SOLD_OUT).with("optionId", Long.toString(optionId)).with("temporary", temporary);
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

    /** 한도 없는 옵션에 옵션 추가 입고·줄이기 — 공구 단위로 한다(결정 80) */
    public static GroupBuyException optionUnlimited() {
        return new GroupBuyException(HttpStatus.CONFLICT, OPTION_UNLIMITED);
    }

    /**
     * 상품정보제공고시가 다 차지 않았다(결정 87) — {@code noNotice}(양식이 하나도 없음), {@code notices}(빈 항목이 있는 양식: 차례·제목·품목·빈 항목 키),
     * {@code unassignedOptions}(어느 양식에도 속하지 않은 옵션 이름).
     */
    public static GroupBuyException noticeIncomplete(boolean noNotice, java.util.List<java.util.Map<String, Object>> notices, java.util.List<String> unassignedOptions) {
        return new GroupBuyException(HttpStatus.CONFLICT, NOTICE_INCOMPLETE).with("noNotice", noNotice).with("notices", notices)
                .with("unassignedOptions", unassignedOptions);
    }

    public static GroupBuyException shippingPolicyNotFound() {
        return new GroupBuyException(HttpStatus.CONFLICT, SHIPPING_POLICY_NOT_FOUND);
    }

    /** 배송비 정책 예약이 기존 줄과 겹치거나 이미 시작된 시각이다 */
    public static GroupBuyException shippingPolicyConflict() {
        return new GroupBuyException(HttpStatus.CONFLICT, SHIPPING_POLICY_CONFLICT);
    }
}
