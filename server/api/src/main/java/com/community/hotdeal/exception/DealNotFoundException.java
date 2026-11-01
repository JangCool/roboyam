package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 딜이 붙은 핫딜 글이 아니다(404) — 글은 있지만 딜 줄이 없거나 핫딜 게시판이 아니다 */
public class DealNotFoundException extends CommonException {

    public DealNotFoundException() {
        super(HttpStatus.NOT_FOUND, "DEAL_NOT_FOUND", new MessageRef("error.DEAL_NOT_FOUND"));
    }
}
