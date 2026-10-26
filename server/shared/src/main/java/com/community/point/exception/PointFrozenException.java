package com.community.point.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 403 — 포인트 동결(기능 제한 POINT_FREEZE) 중이라 포인트를 쓸 수 없다(계약 「1-1 서버 세부」 — 적립은 건너뛰고 사용은 거절) */
public class PointFrozenException extends CommonException {

    public PointFrozenException() {
        super(HttpStatus.FORBIDDEN, "POINT_FROZEN", new MessageRef("error.POINT_FROZEN"));
    }
}
