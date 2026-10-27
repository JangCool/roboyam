package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 그런 대사 이슈가 없다 */
public class ReconcileIssueNotFoundException extends CommonException {

    public ReconcileIssueNotFoundException() {
        super(HttpStatus.NOT_FOUND, "RECONCILE_ISSUE_NOT_FOUND", new MessageRef("error.RECONCILE_ISSUE_NOT_FOUND"));
    }
}
