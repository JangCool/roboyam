package com.community.aimember.llm;

/**
 * 제공사 호출 실패 — 까닭 코드(번들 {@code aiMember.fail.<코드>})와 그때까지 쓴 토큰, 제공사 메시지({@link #detail()} — 오류 본문의 메시지 문자열만 앞 300자,
 * 키 모양·개인정보는 가린다 — {@link AiLlmErrors}). 키·요청 본문은 싣지 않는다.
 */
public class AiLlmException extends RuntimeException {

    /** 키가 틀렸거나 권한이 없다(401·403) — 그 제공사를 쉬게 한다 */
    public static final String KEY_INVALID = "KEY_INVALID";
    /**
     * 결제·크레딧 부족 — 제공사가 돈이 없어 거절했다(429 {@code insufficient_quota} 포함, 판정은 {@link AiLlmErrors}). 키 오류와 같이 그 제공사를 쉬게 한다
     * (결제한 뒤 키를 다시 저장하면 다시 돈다 — 2026-11-11)
     */
    public static final String BILLING_REQUIRED = "BILLING_REQUIRED";
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
    private final String detail;

    public AiLlmException(String reason, int inputTokens, int outputTokens) {
        this(reason, inputTokens, outputTokens, null);
    }

    public AiLlmException(String reason, int inputTokens, int outputTokens, String detail) {
        super(reason, null, false, false);
        this.reason = reason;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.detail = detail;
    }

    /** 이 실패가 그 제공사를 쉬게 하는가(결제·크레딧 부족 · 키 오류) */
    public boolean pausesProvider() {
        return BILLING_REQUIRED.equals(reason) || KEY_INVALID.equals(reason);
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

    /** 제공사 메시지(가린 것, 300자 이하) — 없으면 null */
    public String detail() {
        return detail;
    }
}
