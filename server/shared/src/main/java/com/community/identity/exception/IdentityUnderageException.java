package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/** 403 — 만 19세 미만(commerce.md 12-1). 오류 본문 최상위 {@code adultFrom} = 만 19세가 되는 서비스 날짜({@code YYYY-MM-DD}) */
public class IdentityUnderageException extends CommonException {

    public IdentityUnderageException(LocalDate adultFrom) {
        super(HttpStatus.FORBIDDEN, "IDENTITY_UNDERAGE", new MessageRef("error.IDENTITY_UNDERAGE"));
        extra("adultFrom", adultFrom.toString());
    }
}
