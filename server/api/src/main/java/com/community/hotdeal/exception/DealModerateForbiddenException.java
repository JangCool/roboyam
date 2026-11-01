package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 게시판 매니저·스텝의 핫딜 종료·재개 — 그 글 게시판의 매니저·스텝이 아니거나 정지(제재) 중이다(403) */
public class DealModerateForbiddenException extends CommonException {

    public DealModerateForbiddenException() {
        super(HttpStatus.FORBIDDEN, "DEAL_MODERATE_FORBIDDEN", new MessageRef("error.DEAL_MODERATE_FORBIDDEN"));
    }
}
