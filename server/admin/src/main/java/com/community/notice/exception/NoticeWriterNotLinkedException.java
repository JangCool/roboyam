package com.community.notice.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 로그인한 운영자에게 힘이 살아 있는 운영자 회원 계정이 없다(연결 없음 · 멈춤 — 409 NOTICE_WRITER_NOT_LINKED). 새 글 · 수정 · 본문 이미지 올리기 */
public class NoticeWriterNotLinkedException extends CommonException {

    public NoticeWriterNotLinkedException() {
        super(HttpStatus.CONFLICT, "NOTICE_WRITER_NOT_LINKED", new MessageRef("error.NOTICE_WRITER_NOT_LINKED"));
    }
}
