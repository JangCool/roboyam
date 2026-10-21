package com.community.batch.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 이 DB 배치를 지금 쓸 수 없다(409) — pg_cron 을 쓸 수 없거나(확장·설정·권한) DB 에 이 배치가 등록되어 있지 않다. 목록의 {@code available=false} 인 배치에 켜기/끄기·cron 수정·지금 실행을 하면 난다.
 * 이유는 목록의 {@code unavailableReason} 에 있다.
 */
public class BatchUnavailableException extends CommonException {

    public BatchUnavailableException() {
        super(HttpStatus.CONFLICT, "BATCH_UNAVAILABLE", new MessageRef("error.BATCH_UNAVAILABLE"));
    }
}
