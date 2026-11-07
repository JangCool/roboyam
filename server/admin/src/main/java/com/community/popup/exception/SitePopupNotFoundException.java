package com.community.popup.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 팝업이다(404) */
public class SitePopupNotFoundException extends CommonException {

    public SitePopupNotFoundException() {
        super(HttpStatus.NOT_FOUND, "SITE_POPUP_NOT_FOUND", new MessageRef("error.SITE_POPUP_NOT_FOUND"));
    }
}
