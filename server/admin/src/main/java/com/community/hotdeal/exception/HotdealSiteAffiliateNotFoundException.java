package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 없는 사이트 제휴 몰(404) */
public class HotdealSiteAffiliateNotFoundException extends CommonException {

    public HotdealSiteAffiliateNotFoundException() {
        super(HttpStatus.NOT_FOUND, "HOTDEAL_SITE_AFFILIATE_NOT_FOUND", new MessageRef("error.HOTDEAL_SITE_AFFILIATE_NOT_FOUND"), Map.of(), null);
    }
}
