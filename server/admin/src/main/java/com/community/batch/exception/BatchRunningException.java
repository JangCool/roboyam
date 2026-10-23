package com.community.batch.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 배치가 이미 실행 중이거나 끝나지 않은(대기·실행 중) 실행 요청이 있어 지금 실행·다시 실행을 요청할 수 없다(409). */
public class BatchRunningException extends CommonException {

    public BatchRunningException() {
        super(HttpStatus.CONFLICT, "BATCH_RUNNING", new MessageRef("error.BATCH_RUNNING"));
    }
}
