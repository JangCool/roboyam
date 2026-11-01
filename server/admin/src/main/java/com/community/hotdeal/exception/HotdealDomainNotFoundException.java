package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 없는 링크 규칙(404) */
public class HotdealDomainNotFoundException extends CommonException {

    public HotdealDomainNotFoundException() {
        super(HttpStatus.NOT_FOUND, "HOTDEAL_DOMAIN_NOT_FOUND", new MessageRef("error.HOTDEAL_DOMAIN_NOT_FOUND"), Map.of(), null);
    }
}
