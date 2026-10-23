package com.community.media.exception;

/**
 * 이미지를 읽을 수 없다(깨졌거나 규칙에 맞지 않는 구조) — 이미지 처리 안쪽(코덱)이 던지는 내부 예외다. 응답으로 바로 나가지 않고,
 * 부르는 쪽 처리기가 칸 오류({@code fields.file}, 「이미지를 읽을 수 없어요」)로 바꾼다.
 */
public class ImageUnreadableException extends RuntimeException {

    public ImageUnreadableException(String message) {
        super(message, null, false, false);
    }

    public ImageUnreadableException(String message, Throwable cause) {
        super(message, cause, false, false);
    }
}
