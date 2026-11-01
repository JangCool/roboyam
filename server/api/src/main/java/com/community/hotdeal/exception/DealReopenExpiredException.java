package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 작성자 되돌리기(D2) 기한(종료 뒤 24시간)이 지났거나 작성자가 끝낸 딜이 아니다(409 — 자동·운영진 종료는 운영진만 재개) */
public class DealReopenExpiredException extends CommonException {

    public DealReopenExpiredException() {
        super(HttpStatus.CONFLICT, "DEAL_REOPEN_EXPIRED", new MessageRef("error.DEAL_REOPEN_EXPIRED"));
    }
}
