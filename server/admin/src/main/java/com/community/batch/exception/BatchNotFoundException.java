package com.community.batch.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 카탈로그에 없는 배치다(404). */
public class BatchNotFoundException extends CommonException {

    public BatchNotFoundException() {
        super(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", new MessageRef("error.BATCH_NOT_FOUND"));
    }
}
