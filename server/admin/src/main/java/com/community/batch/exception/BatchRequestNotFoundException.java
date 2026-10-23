package com.community.batch.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 배치 실행 요청이다(404). */
public class BatchRequestNotFoundException extends CommonException {

    public BatchRequestNotFoundException() {
        super(HttpStatus.NOT_FOUND, "BATCH_REQUEST_NOT_FOUND", new MessageRef("error.BATCH_REQUEST_NOT_FOUND"));
    }
}
