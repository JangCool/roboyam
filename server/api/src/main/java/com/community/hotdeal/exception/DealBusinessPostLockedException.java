package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 사업자 핫딜 글(딜 {@code source = BUSINESS})은 작성자도 고치기·지우기·되돌리기를 할 수 없다(403 — biz-hotdeal-contract 규칙 11). 내리는 길은 [품절로 종료]·운영자 숨김·신청 건 취소뿐
 */
public class DealBusinessPostLockedException extends CommonException {

    public DealBusinessPostLockedException() {
        super(HttpStatus.FORBIDDEN, "DEAL_BUSINESS_POST_LOCKED", new MessageRef("error.DEAL_BUSINESS_POST_LOCKED"));
    }
}
