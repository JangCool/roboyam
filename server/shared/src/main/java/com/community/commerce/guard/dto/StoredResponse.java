package com.community.commerce.guard.dto;

/**
 * 멱등 키로 다시 줄 응답 — HTTP 상태와 본문(JSON 글, 16KB 까지). {@code replayed} 는 이번 응답이 처음 처리한 것인지(false) 저장해 둔 것을 다시 준 것인지(true).
 */
public record StoredResponse(int status, String body, boolean replayed) {

    /** 저장할 수 있는 본문의 최대 크기(UTF-8 바이트) — {@code idempotency_requests.ck_idempotency_body_size} 와 같다 */
    public static final int MAX_BODY_BYTES = 16 * 1024;

    public static StoredResponse fresh(int status, String body) {
        return new StoredResponse(status, body, false);
    }
}
