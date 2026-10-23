package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없거나 내 것이 아닌 신뢰 기기(404 {@code MFA_DEVICE_NOT_FOUND}) */
public class MfaDeviceNotFoundException extends CommonException {

    public MfaDeviceNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MFA_DEVICE_NOT_FOUND", new MessageRef("error.MFA_DEVICE_NOT_FOUND"));
    }
}
