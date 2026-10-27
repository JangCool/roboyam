package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 대사 배치에 끝나지 않은 실행 요청이 이미 있다(한 번에 하나) */
public class ReconcileAlreadyRunningException extends CommonException {

    public ReconcileAlreadyRunningException() {
        super(HttpStatus.CONFLICT, "RECONCILE_ALREADY_RUNNING", new MessageRef("error.RECONCILE_ALREADY_RUNNING"));
    }
}
