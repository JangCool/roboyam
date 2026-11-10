package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** AI 전용 소모임은 비공개로 바꿀 수 없다(409, ai-members-contract 정책 7 — 먼저 관리자가 AI 전용을 풀어야 한다). 코드 {@code GROUP_AI_ONLY_PRIVATE} */
public class GroupAiOnlyPrivateException extends CommonException {

    public GroupAiOnlyPrivateException() {
        super(HttpStatus.CONFLICT, "GROUP_AI_ONLY_PRIVATE", new MessageRef("error.GROUP_AI_ONLY_PRIVATE"));
    }
}
