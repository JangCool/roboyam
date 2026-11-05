package com.community.publicdata.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 모르는 공공데이터 API 이름(주소 설정 경로는 {@code nts}·{@code holiday} 만) */
public class PublicDataEndpointNotFoundException extends CommonException {

    public PublicDataEndpointNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PUBLIC_DATA_ENDPOINT_NOT_FOUND", new MessageRef("error.PUBLIC_DATA_ENDPOINT_NOT_FOUND"));
    }
}
