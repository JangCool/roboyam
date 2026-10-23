package com.community.batch.core;

/**
 * 대상 한 건을 처리하지 못했다 — 실패 건({@code batch_run_failures})에 오류 코드를 남기고 싶을 때 처리 쪽이 던진다. 문구는 실행기가 비밀값·개인정보를 가려 남긴다
 * ({@link BatchErrorText}). 다른 예외는 가장 깊은 원인의 클래스 이름이 오류 코드가 된다.
 */
public class BatchItemException extends RuntimeException {

    private final String code;

    /**
     * @param code    오류 코드(대문자·숫자·밑줄 80자, 예 {@code LOT_BALANCE_MISMATCH})
     * @param message 사람이 읽을 사유 — 비밀값·개인정보를 싣지 않는다(실행기가 한 번 더 가린다)
     */
    public BatchItemException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BatchItemException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
