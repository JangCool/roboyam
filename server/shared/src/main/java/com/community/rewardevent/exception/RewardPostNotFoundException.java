package com.community.rewardevent.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 댓글 추첨 미리 보기의 글이 없다(지운 글 포함) — 다른 업무의 글 없음과 같은 코드 */
public class RewardPostNotFoundException extends CommonException {

    public RewardPostNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", new MessageRef("error.POST_NOT_FOUND"));
    }
}
