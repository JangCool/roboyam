package com.community.operator.dto.request;

/**
 * 계정 상태 — 보낸 칸만 바꾼다. 값 그대로다(예: {@code locked: true} 는 잠금). {@code locked} 를 바꾸려면 {@code operator.lock}, 나머지는 {@code operator.manage}.
 */
public record OperatorStatusRequest(Boolean approved, Boolean locked, Boolean expired, Boolean enabled) {
}
