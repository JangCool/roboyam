package com.community.bizapplication.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 사업자 신청 건의 업무 오류(biz-application-contract). 문구는 번들 {@code error.<코드>}. 화면은 {@code BIZ_APPLICATION_STATE_CHANGED} 면 「이미 바뀌었어요」 + 다시 읽기.
 * 자격 오류(409 {@code BUSINESS_NOT_APPROVED}·{@code BUSINESS_RE_REVIEW})는 사업자 신원의 {@code BusinessException} 이 그대로 나간다.
 */
public class BizApplicationException extends CommonException {

    public static final String NOT_FOUND = "BIZ_APPLICATION_NOT_FOUND";
    public static final String STATE_CHANGED = "BIZ_APPLICATION_STATE_CHANGED";
    public static final String SLOT_FULL = "BIZ_SLOT_FULL";
    public static final String DEPOSIT_INCOMPLETE = "BIZ_DEPOSIT_INCOMPLETE";
    public static final String PRODUCT_CLOSED = "BIZ_PRODUCT_CLOSED";
    public static final String CONTRACT_NOT_FOUND = "BIZ_CONTRACT_NOT_FOUND";
    public static final String SELF_APPROVAL_DENIED = "BIZ_SELF_APPROVAL_DENIED";
    public static final String ADVERTISER_NOT_FOUND = "AD_ADVERTISER_NOT_FOUND";
    public static final String PAUSE_LIMIT = "BIZ_PAUSE_LIMIT";
    public static final String CONTACT_MISSING = "BIZ_CONTACT_MISSING";
    /** 운영 주체가 돈을 받을 수 없는 단계라 온라인 결제를 시작할 수 없다(사업자 화면 — 「운영자에게 문의」) */
    public static final String PAYMENT_UNAVAILABLE = "BIZ_PAYMENT_UNAVAILABLE";
    /** 운영 주체가 돈을 받을 수 없는 단계라 유료 신청을 승인·입금 확정할 수 없다(관리자 화면) */
    public static final String OPERATOR_STAGE = "BIZ_OPERATOR_STAGE";

    private BizApplicationException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    /** 없거나 남의 신청 건(다른 회원의 건은 있는지도 알리지 않는다) */
    public static BizApplicationException notFound() {
        return new BizApplicationException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    /** 처리하려던 사이 상태가 바뀌었거나 그 상태에서 할 수 없는 처리 */
    public static BizApplicationException stateChanged() {
        return new BizApplicationException(HttpStatus.CONFLICT, STATE_CHANGED);
    }

    /** 그 기간에 자리(동시 노출 한도)가 없다 — 관리자·공개 같은 코드 */
    public static BizApplicationException slotFull() {
        return new BizApplicationException(HttpStatus.CONFLICT, SLOT_FULL);
    }

    /** 수동 결제 확정에 계약서 사본(대체되지 않은 1장 이상)과 입금 확인이 둘 다 있어야 한다 */
    public static BizApplicationException depositIncomplete() {
        return new BizApplicationException(HttpStatus.CONFLICT, DEPOSIT_INCOMPLETE);
    }

    /** 이 상품(광고 종류·노출 자리)은 지금 신청을 받지 않는다(요금표 accepting = false) */
    public static BizApplicationException productClosed() {
        return new BizApplicationException(HttpStatus.CONFLICT, PRODUCT_CLOSED);
    }

    /**
     * 온라인 결제 시작 — 운영 주체가 개인 단계이거나 통신판매업 신고번호가 없다({@code CommerceSalesStore.operatorCanSell} = false, 사용자 결정 2026-11-07). 이미 열린 결제의 완료는 그대로 받는다
     */
    public static BizApplicationException paymentUnavailable() {
        return new BizApplicationException(HttpStatus.CONFLICT, PAYMENT_UNAVAILABLE);
    }

    /** 유료 승인·입금 확정(예약 확정) — 운영 주체가 개인 단계이거나 통신판매업 신고번호가 없다(같은 판정) */
    public static BizApplicationException operatorStage() {
        return new BizApplicationException(HttpStatus.CONFLICT, OPERATOR_STAGE);
    }

    /** 운영자 등록 건을 등록한 운영자가 직접 승인하려 했는데 설정이 자기 승인을 막는다(roboyam.biz.self-approval = deny) */
    public static BizApplicationException selfApprovalDenied() {
        return new BizApplicationException(HttpStatus.CONFLICT, SELF_APPROVAL_DENIED);
    }

    /** 한 건에 일시 정지할 수 있는 횟수(설정 {@code roboyam.biz.pause-limit}, 기본 3)를 다 썼다 — 사업자·운영자 대신 처리 같은 셈 */
    public static BizApplicationException pauseLimit() {
        return new BizApplicationException(HttpStatus.CONFLICT, PAUSE_LIMIT);
    }

    /** 광고주가 없다(광고 일정표 — 계정 없는 광고주) */
    /** 온라인 결제를 시작하려는데 사업자 신원에 연락처(전화)가 없다 — 결제창(포트원)이 구매자 전화번호를 꼭 받는다 */
    public static BizApplicationException contactMissing() {
        return new BizApplicationException(HttpStatus.BAD_REQUEST, CONTACT_MISSING);
    }

    public static BizApplicationException advertiserNotFound() {
        return new BizApplicationException(HttpStatus.NOT_FOUND, ADVERTISER_NOT_FOUND);
    }

    public static final String ADVERTISER_HAS_BOOKINGS = "AD_ADVERTISER_HAS_BOOKINGS";
    public static final String ADVERTISER_HAS_OPEN_BOOKINGS = "AD_ADVERTISER_HAS_OPEN_BOOKINGS";
    public static final String ADVERTISER_BUSINESS = "AD_ADVERTISER_BUSINESS";

    /** 신청 건이 하나라도 있는 광고주는 지울 수 없다(광고주 관리 — ad-advertiser-contract 규칙 6, 보관으로) */
    public static BizApplicationException advertiserHasBookings() {
        return new BizApplicationException(HttpStatus.CONFLICT, ADVERTISER_HAS_BOOKINGS);
    }

    /** 끝나지 않은 건(진행·예약·대기)이 있으면 보관할 수 없다 */
    public static BizApplicationException advertiserHasOpenBookings() {
        return new BizApplicationException(HttpStatus.CONFLICT, ADVERTISER_HAS_OPEN_BOOKINGS);
    }

    /** 사업자 광고주는 보관·삭제가 없다(사업자 정지·탈퇴를 따른다) */
    public static BizApplicationException advertiserBusiness() {
        return new BizApplicationException(HttpStatus.CONFLICT, ADVERTISER_BUSINESS);
    }

    /** 계약서 사본이 없다(다른 신청 건의 것 포함) */
    public static BizApplicationException contractNotFound() {
        return new BizApplicationException(HttpStatus.NOT_FOUND, CONTRACT_NOT_FOUND);
    }
}
