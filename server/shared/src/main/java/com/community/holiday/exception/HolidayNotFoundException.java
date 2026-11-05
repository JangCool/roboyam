package com.community.holiday.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 쉬는 날 줄이 없다(이미 지웠거나 다시 받으며 사라졌다) */
public class HolidayNotFoundException extends CommonException {

    public HolidayNotFoundException() {
        super(HttpStatus.NOT_FOUND, "HOLIDAY_NOT_FOUND", new MessageRef("error.HOLIDAY_NOT_FOUND"));
    }
}
