package com.community.batch.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 「실패 건만 다시」할 미해결 실패 건이 없다(409) — 건별 실패를 기록하지 않는 배치(DB 배치)도 여기에 해당한다. */
public class BatchNoFailuresException extends CommonException {

    public BatchNoFailuresException() {
        super(HttpStatus.CONFLICT, "BATCH_NO_FAILURES", new MessageRef("error.BATCH_NO_FAILURES"));
    }
}
