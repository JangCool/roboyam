package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 {@code ANONYMOUS_TARGET_UNAVAILABLE} — 익명(봉인) 대상에게 지금 제재·스텝 처리를 할 수 없다. 까닭(본인 · 탈퇴 · 운영자 회원 계정 · 같은 게시판 매니저·스텝)을 가리지 않는
 * 중립 오류다 — 대상 보호 409({@code BOARD_STAFF_TARGET_PROTECTED})가 익명 작성자의 신분을 드러내지 않게(anonymous-board-contract 「서버 구현 결과」 2026-11-11).
 */
public class AnonymousTargetUnavailableException extends CommonException {

    public static final String CODE = "ANONYMOUS_TARGET_UNAVAILABLE";

    public AnonymousTargetUnavailableException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.ANONYMOUS_TARGET_UNAVAILABLE"));
    }
}
