package com.community.point.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 오늘(한국 시간) 이미 출석했다. 같은 순간 두 번 눌러도 하나만 성공하고 나머지는 이것이다(계약 「1-3 출석체크」) */
public class AttendanceAlreadyDoneException extends CommonException {

    public AttendanceAlreadyDoneException() {
        super(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_DONE", new MessageRef("error.ATTENDANCE_ALREADY_DONE"));
    }
}
