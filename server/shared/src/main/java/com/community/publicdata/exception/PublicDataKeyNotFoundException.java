package com.community.publicdata.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 화면에서 저장한 공공데이터포털 서비스 키가 없는데 지우려 했다(환경 변수 키는 화면에서 지울 수 없다) */
public class PublicDataKeyNotFoundException extends CommonException {

    public PublicDataKeyNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PUBLIC_DATA_KEY_NOT_FOUND", new MessageRef("error.PUBLIC_DATA_KEY_NOT_FOUND"));
    }
}
