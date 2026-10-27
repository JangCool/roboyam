package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 처리된 대사 이슈다 */
public class ReconcileIssueNotOpenException extends CommonException {

    public ReconcileIssueNotOpenException() {
        super(HttpStatus.CONFLICT, "RECONCILE_ISSUE_NOT_OPEN", new MessageRef("error.RECONCILE_ISSUE_NOT_OPEN"));
    }
}
