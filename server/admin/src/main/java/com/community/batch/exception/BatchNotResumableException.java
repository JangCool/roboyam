package com.community.batch.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 이 실행은 「실패 지점부터 다시」를 할 수 없다(409) — 실패로 끝나지 않았거나, 체크포인트가 없거나, 작업이 지원하지 않거나(DB 배치 포함), 이미 이어 받은 실행이 있다(한 번만 — 확정 규칙 3).
 */
public class BatchNotResumableException extends CommonException {

    public BatchNotResumableException() {
        super(HttpStatus.CONFLICT, "BATCH_NOT_RESUMABLE", new MessageRef("error.BATCH_NOT_RESUMABLE"));
    }
}
