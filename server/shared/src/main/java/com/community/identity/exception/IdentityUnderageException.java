package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/** 403 — 만 19세 미만(commerce.md 12-1). 오류 본문 최상위 {@code adultFrom} = 만 19세가 되는 서비스 날짜({@code YYYY-MM-DD}) */
public class IdentityUnderageException extends CommonException {

    public IdentityUnderageException(LocalDate adultFrom) {
        this(adultFrom, "error.IDENTITY_UNDERAGE");
    }

    /** 같은 코드에 그 자리의 문구(예: 로보티콘 작가 정산 정보·유료 희망 — 「무료 팩은 만들 수 있어요」) */
    public IdentityUnderageException(LocalDate adultFrom, String messageKey) {
        super(HttpStatus.FORBIDDEN, "IDENTITY_UNDERAGE", new MessageRef(messageKey));
        extra("adultFrom", adultFrom.toString());
    }
}
