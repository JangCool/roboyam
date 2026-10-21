package com.community.zconfiguration.response;

import java.time.Instant;

/**
 * 모든 응답에 붙는 메타 — 서버 시각(UTC ISO-8601)·요청 ID·응답 문구 언어({@code ko}|{@code en}).
 * 값은 {@link ApiResponse} 팩터리가 현재 요청 문맥({@link RequestIdHolder})에서 채운다. Controller 가 만들지 않는다.
 */
public record ResponseMeta(Instant timestamp, String requestId, String locale) {
}
