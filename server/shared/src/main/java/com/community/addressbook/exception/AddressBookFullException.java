package com.community.addressbook.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 주소록이 가득 찼다(409 — 한 회원 최대 {@code AddressBookService.MAX}개) */
public class AddressBookFullException extends CommonException {

    public AddressBookFullException(int max) {
        super(HttpStatus.CONFLICT, "ADDRESS_BOOK_FULL", new MessageRef("error.ADDRESS_BOOK_FULL", max));
    }
}
