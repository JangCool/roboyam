package com.community.sociallogin.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 모르는 공급자이거나, 꺼져 있거나, 키가 없어 지금 쓸 수 없는 공급자(404). 셋을 구분해 알리지 않는다 — 공급자 존재 여부 외의 정보를 내지 않는다.
 */
public class SocialProviderNotFoundException extends CommonException {

    public SocialProviderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", new MessageRef("error.PROVIDER_NOT_FOUND"));
    }
}
