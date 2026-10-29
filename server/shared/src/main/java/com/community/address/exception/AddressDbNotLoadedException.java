package com.community.address.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 자체 주소 DB 를 아직 한 번도 올리지 않았다(503) — 회원 화면은 직접 입력을 권한다 */
public class AddressDbNotLoadedException extends CommonException {

    public AddressDbNotLoadedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ADDRESS_DB_NOT_LOADED", new MessageRef("error.ADDRESS_DB_NOT_LOADED"));
    }
}
