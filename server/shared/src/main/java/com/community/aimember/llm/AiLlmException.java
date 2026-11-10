package com.community.aimember.llm;

/**
 * 제공사 호출 실패 — 까닭 코드(번들 {@code aiMember.fail.<코드>})와 그때까지 쓴 토큰. 키·요청·응답 본문은 싣지 않는다.
 */
public class AiLlmException extends RuntimeException {

    /** 키가 틀렸거나 권한이 없다(401·403) */
    public static final String KEY_INVALID = "KEY_INVALID";
    /** 요청 한도(429) */
    public static final String RATE_LIMITED = "RATE_LIMITED";
    /** 응답이 없다 */
    public static final String TIMEOUT = "TIMEOUT";
    /** 제공사가 거절했다(안전 정책) */
    public static final String REFUSED = "REFUSED";
    /** 글자가 없는 응답 · 형식이 틀린 응답 */
    public static final String EMPTY = "EMPTY";
    /** 그 밖의 제공사 오류(4xx·5xx) */
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";

    private final String reason;
    private final int inputTokens;
    private final int outputTokens;

    public AiLlmException(String reason, int inputTokens, int outputTokens) {
        super(reason, null, false, false);
        this.reason = reason;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public String reason() {
        return reason;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }
}
