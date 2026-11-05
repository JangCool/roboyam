package com.community.holiday.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 공공데이터·기본값 공휴일은 지울 수 없다(읽기만 — 회사 휴무일만 운영자가 더하고 지운다) */
public class HolidayNotEditableException extends CommonException {

    public HolidayNotEditableException() {
        super(HttpStatus.CONFLICT, "HOLIDAY_NOT_EDITABLE", new MessageRef("error.HOLIDAY_NOT_EDITABLE"));
    }
}
