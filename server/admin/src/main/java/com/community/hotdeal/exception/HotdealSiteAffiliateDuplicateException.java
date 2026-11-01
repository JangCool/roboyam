package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이미 있는 사이트 제휴 몰 도메인(409, fields.domain) */
public class HotdealSiteAffiliateDuplicateException extends CommonException {

    public HotdealSiteAffiliateDuplicateException() {
        super(HttpStatus.CONFLICT, "HOTDEAL_SITE_AFFILIATE_DUPLICATE", new MessageRef("error.HOTDEAL_SITE_AFFILIATE_DUPLICATE"), Map.of("domain", new MessageRef("error.HOTDEAL_SITE_AFFILIATE_DUPLICATE")), null);
    }
}
