package com.community.address.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그런 적재 작업이 없다(404) */
public class AddressImportJobNotFoundException extends CommonException {

    public AddressImportJobNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ADDRESS_IMPORT_JOB_NOT_FOUND", new MessageRef("error.ADDRESS_IMPORT_JOB_NOT_FOUND"));
    }
}
