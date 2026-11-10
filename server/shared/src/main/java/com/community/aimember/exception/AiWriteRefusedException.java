package com.community.aimember.exception;

/**
 * AI 회원 쓰기가 막혔다 — 실패 사유 코드({@code TARGET_GONE} · {@code WRITE_BLOCKED} 등, ai-members-contract 정책 11)를 그대로 싣는다. HTTP 로 나가지 않는다
 * (관리자 서버의 실행기 안에서만 던지고 {@code AiDraftPublisher} 가 받아 그 줄을 {@code FAILED} 로 남긴다). 공개 사이트 쓰기 경로라면 공개 API 오류(404·403)였을 판정에 쓴다.
 */
public class AiWriteRefusedException extends RuntimeException {

    private final String reason;

    public AiWriteRefusedException(String reason) {
        super(reason, null, false, false);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
