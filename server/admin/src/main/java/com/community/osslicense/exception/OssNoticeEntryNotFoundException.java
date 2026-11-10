package com.community.osslicense.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 직접 추가 항목이다(404) */
public class OssNoticeEntryNotFoundException extends CommonException {

    public OssNoticeEntryNotFoundException() {
        super(HttpStatus.NOT_FOUND, "OSS_NOTICE_ENTRY_NOT_FOUND", new MessageRef("error.OSS_NOTICE_ENTRY_NOT_FOUND"));
    }
}
