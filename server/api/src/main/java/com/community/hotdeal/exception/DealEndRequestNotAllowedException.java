package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 종료 요청 자격 미달(403) — 그 게시판 쓰기 자격(writeMin·첫 등급 아님) + 이메일 인증 + 정지 아님 */
public class DealEndRequestNotAllowedException extends CommonException {

    public DealEndRequestNotAllowedException() {
        super(HttpStatus.FORBIDDEN, "DEAL_END_REQUEST_NOT_ALLOWED", new MessageRef("error.DEAL_END_REQUEST_NOT_ALLOWED"));
    }
}
