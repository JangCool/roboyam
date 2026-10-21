package com.community.media.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 파일 저장소(디스크·네트워크·권한)에 쓰지 못했다. 원인(입출력 오류)은 던지는 쪽이 로그에 남기고 응답에는 싣지 않는다 */
public class StorageUnavailableException extends CommonException {

    public StorageUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", new MessageRef("error.STORAGE_UNAVAILABLE"));
    }
}
