package com.community.site.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 그 ID 의 로고 묶음이 없다 */
public class SiteLogoBundleNotFoundException extends CommonException {

    public SiteLogoBundleNotFoundException() {
        super(HttpStatus.NOT_FOUND, "SITE_LOGO_BUNDLE_NOT_FOUND", new MessageRef("error.SITE_LOGO_BUNDLE_NOT_FOUND"));
    }
}
