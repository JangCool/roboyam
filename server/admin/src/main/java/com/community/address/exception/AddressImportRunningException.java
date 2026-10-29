package com.community.address.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 주소 DB 를 올리고 있다(409) — 적재는 사이트에 하나씩 */
public class AddressImportRunningException extends CommonException {

    public AddressImportRunningException() {
        super(HttpStatus.CONFLICT, "ADDRESS_IMPORT_RUNNING", new MessageRef("error.ADDRESS_IMPORT_RUNNING"));
    }
}
