package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 작성자가 같은 제목·본문을 정한 시간 안에 다시 씀(409) */
public class DuplicatePostException extends CommonException {

    public DuplicatePostException(int minutes) {
        super(HttpStatus.CONFLICT, "DUPLICATE_POST", new MessageRef("error.DUPLICATE_POST", minutes));
    }
}
