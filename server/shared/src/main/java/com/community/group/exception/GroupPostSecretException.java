package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 400 — 비밀글은 소모임 밖으로 공개할 수 없다(V168 — 소모임 게시판의 비밀글). 공개 요청 · 승인 · 모임장·매니저의 직접 공개 · 공개 소모임의 다시 공개가 같은 코드를 낸다.
 * 비공개로 되돌리기·멤버 전용으로 돌리기는 막지 않는다.
 */
public class GroupPostSecretException extends CommonException {

    public GroupPostSecretException() {
        super(HttpStatus.BAD_REQUEST, "GROUP_POST_SECRET", new MessageRef("error.GROUP_POST_SECRET"));
    }
}
