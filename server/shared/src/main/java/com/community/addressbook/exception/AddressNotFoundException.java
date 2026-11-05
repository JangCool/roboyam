package com.community.addressbook.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없거나 남의 배송지(404 — 남의 것이 있는지 알리지 않는다) */
public class AddressNotFoundException extends CommonException {

    public AddressNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", new MessageRef("error.ADDRESS_NOT_FOUND"));
    }
}
