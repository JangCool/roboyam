package com.community.batch.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 배치가 이미 실행 중이라 지금 실행할 수 없다(409). */
public class BatchRunningException extends CommonException {

    public BatchRunningException() {
        super(HttpStatus.CONFLICT, "BATCH_RUNNING", new MessageRef("error.BATCH_RUNNING"));
    }
}
