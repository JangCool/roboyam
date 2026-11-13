package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 운영자가 이 글의 대표 썸네일을 정했다 — 글쓴이는 바꿀 수 없다(409 THUMBNAIL_LOCKED, docs/features/list-thumbnail-contract.md 「2단계」). 글 고치기 전체가 저장되지 않는다 */
public class ThumbnailLockedException extends CommonException {

    public ThumbnailLockedException() {
        super(HttpStatus.CONFLICT, "THUMBNAIL_LOCKED", new MessageRef("error.THUMBNAIL_LOCKED"));
    }
}
