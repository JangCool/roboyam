package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 의견 고정 요청의 업무 오류 — 코드마다 문구 {@code error.<code>}.
 *
 * <ul>
 *   <li>409 {@code PIN_REQUEST_OFF} — 이 게시판은 고정 요청을 받지 않는다(정책 {@code pinRequestReviewers} 가 비었다)</li>
 *   <li>409 {@code PIN_REQUEST_NOT_OPINION} — 내 의견(입장 있는 원댓글)이 아니다(일반 댓글·답글)</li>
 *   <li>409 {@code OPINION_ALREADY_PINNED} — 이미 고정된 의견</li>
 *   <li>409 {@code OPINION_UNAVAILABLE} — 숨기거나 지운 의견(요청·승인 모두)</li>
 *   <li>409 {@code PIN_REQUEST_EXISTS} — 이미 대기 중인 요청이 있다</li>
 *   <li>409 {@code PIN_REQUEST_DECIDED} — 이미 승인·거절된 요청</li>
 *   <li>404 {@code PIN_REQUEST_NOT_FOUND} — 없는 요청(취소됐거나 형식이 틀림)</li>
 *   <li>403 {@code PIN_REQUEST_FORBIDDEN} — 이 요청을 확인할 수 있는 사람이 아니다(내 요청도 내가 확인할 수 없다)</li>
 * </ul>
 */
public class PinRequestException extends CommonException {

    public static final String OFF = "PIN_REQUEST_OFF";
    public static final String NOT_OPINION = "PIN_REQUEST_NOT_OPINION";
    public static final String ALREADY_PINNED = "OPINION_ALREADY_PINNED";
    public static final String UNAVAILABLE = "OPINION_UNAVAILABLE";
    public static final String EXISTS = "PIN_REQUEST_EXISTS";
    public static final String DECIDED = "PIN_REQUEST_DECIDED";
    public static final String NOT_FOUND = "PIN_REQUEST_NOT_FOUND";
    public static final String FORBIDDEN = "PIN_REQUEST_FORBIDDEN";

    private PinRequestException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    public static PinRequestException conflict(String code) {
        return new PinRequestException(HttpStatus.CONFLICT, code);
    }

    public static PinRequestException notFound() {
        return new PinRequestException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    public static PinRequestException forbidden() {
        return new PinRequestException(HttpStatus.FORBIDDEN, FORBIDDEN);
    }
}
