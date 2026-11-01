package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 종료된 딜의 딜 칸은 고칠 수 없다(409) — 기간 종료(EXPIRED)는 고칠 수 있다 */
public class DealEndedException extends CommonException {

    public DealEndedException() {
        super(HttpStatus.CONFLICT, "DEAL_ENDED", new MessageRef("error.DEAL_ENDED"));
    }
}