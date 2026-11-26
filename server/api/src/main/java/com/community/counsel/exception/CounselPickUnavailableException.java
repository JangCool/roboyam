package com.community.counsel.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 사연·이 답은 고를 수 없다 — 위험 사연 · 판정 전 · 지우거나 숨긴 답 · 답글 · 상담사가 아닌 AI 의 답(409) */
public class CounselPickUnavailableException extends CommonException {

    public CounselPickUnavailableException() {
        super(HttpStatus.CONFLICT, "COUNSEL_PICK_UNAVAILABLE", new MessageRef("error.COUNSEL_PICK_UNAVAILABLE"));
    }
}
