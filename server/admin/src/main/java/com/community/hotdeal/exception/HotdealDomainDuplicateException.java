package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이미 있는 링크 규칙 도메인(409, fields.domain) */
public class HotdealDomainDuplicateException extends CommonException {

    public HotdealDomainDuplicateException() {
        super(HttpStatus.CONFLICT, "HOTDEAL_DOMAIN_DUPLICATE", new MessageRef("error.HOTDEAL_DOMAIN_DUPLICATE"), Map.of("domain", new MessageRef("error.HOTDEAL_DOMAIN_DUPLICATE")), null);
    }
}
