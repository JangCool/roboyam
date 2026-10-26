package com.community.point.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 쓸 수 있는 포인트(소멸일이 지나지 않은 무상 + 유상)가 모자라다(commerce.md 5-4 「잔액 부족은 실패, 음수 불가」) */
public class PointInsufficientException extends CommonException {

    public PointInsufficientException() {
        super(HttpStatus.CONFLICT, "POINT_INSUFFICIENT", new MessageRef("error.POINT_INSUFFICIENT"));
    }
}
