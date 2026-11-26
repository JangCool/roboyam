package com.community.counsel.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** AI 상담소 말머리 글이 아니다 · 상담소가 없는 게시판(404 — ai-love-counsel-contract) */
public class CounselNotFoundException extends CommonException {

    public CounselNotFoundException() {
        super(HttpStatus.NOT_FOUND, "COUNSEL_NOT_FOUND", new MessageRef("error.COUNSEL_NOT_FOUND"));
    }
}
