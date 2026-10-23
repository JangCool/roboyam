package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 이미지 올리기를 막은 게시판(유효값 false)의 글에 <b>새로</b> 넣은 이미지가 있다(422) — 고치기는 그 글이 이미 쓰던 이미지는 그대로 둘 수 있다.
 * community {@code docs/api-board-settings.md} 16-3 과 같은 코드.
 */
public class ImageUploadNotAllowedException extends CommonException {

    public ImageUploadNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "IMAGE_UPLOAD_NOT_ALLOWED", new MessageRef("error.IMAGE_UPLOAD_NOT_ALLOWED"));
    }
}
