package com.community.site.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 기본 제공 로고 묶음(「캐릭터 로고」·「글자 로고」)은 고칠 수 없다 */
public class SiteLogoBundleNotEditableException extends CommonException {

    public SiteLogoBundleNotEditableException() {
        super(HttpStatus.CONFLICT, "SITE_LOGO_BUNDLE_NOT_EDITABLE", new MessageRef("error.SITE_LOGO_BUNDLE_NOT_EDITABLE"));
    }
}
