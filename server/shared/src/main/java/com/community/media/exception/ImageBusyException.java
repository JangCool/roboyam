package com.community.media.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미지를 처리할 자리가 없다(503) — 동시 처리 수 제한({@code ImageGate})을 기다리다 시간이 지났거나 SVG 를 그릴 스레드 자리가 없다. 잠시 뒤 다시 시도하면 된다 */
public class ImageBusyException extends CommonException {

    public ImageBusyException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_BUSY", new MessageRef("error.IMAGE_BUSY"));
    }
}
