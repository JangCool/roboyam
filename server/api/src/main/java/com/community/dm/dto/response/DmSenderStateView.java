package com.community.dm.dto.response;

import java.time.Instant;

/** 받는 사람과 상관없이 나 때문에 못 보내는 이유 — user-web {@code DmSenderState}. {@code availableFrom} 은 {@code TOO_NEW} 일 때 가입 + 7일 */
public record DmSenderStateView(boolean canSend, String reason, Instant availableFrom) {
}
