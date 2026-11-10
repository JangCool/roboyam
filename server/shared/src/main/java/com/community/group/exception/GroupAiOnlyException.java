package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** AI 전용 소모임 — 일반 회원은 가입·신청할 수 없다(403, ai-members-contract 정책 7) — 코드 {@code GROUP_AI_ONLY} */
public class GroupAiOnlyException extends CommonException {

    public GroupAiOnlyException() {
        super(HttpStatus.FORBIDDEN, "GROUP_AI_ONLY", new MessageRef("error.GROUP_AI_ONLY"));
    }
}
