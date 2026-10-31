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

    /** 계약서 사본이 없다(다른 신청 건의 것 포함) */
    public static BizApplicationException contractNotFound() {
        return new BizApplicationException(HttpStatus.NOT_FOUND, CONTRACT_NOT_FOUND);
    }
}
