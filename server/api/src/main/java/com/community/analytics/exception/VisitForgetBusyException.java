package com.community.analytics.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 대기 중인 방문 기록 삭제 요청이 전체 상한에 닿았다(503, 몇 분 뒤 다시) — 서버 여러 대가 함께 지키는 DB 상한, {@code VisitForgetStore#MAX_PENDING} */
public class VisitForgetBusyException extends CommonException {

    public VisitForgetBusyException(long retryAfterSeconds) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "VISIT_FORGET_BUSY", new MessageRef("error.VISIT_FORGET_BUSY"), Map.of(), retryAfterSeconds);
    }
}
