package com.community.commerce.guard.dto;

/** {@code request_rate_limits} 한 줄 — 종류 코드({@link RequestAction} 이름, 모르는 값일 수 있다)와 1분 한도 */
public record RequestRateLimitRow(String action, int limitPerMinute) {
}
