package com.community.zconfiguration.response;

import com.community.zconfiguration.exception.ErrorResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 모든 JSON 응답의 공통 겉모양 (.claude/guides/backend/exception.md 2-1). {@code null} 필드는 나가지 않는다.
 *
 * <pre>{@code
 * 성공: { "success": true,  "data": { ... },  "meta": { "timestamp": "...", "requestId": "...", "locale": "ko" } }
 * 실패: { "success": false, "error": { "code": "...", "message": "...", "fields": {}, "retryAfterSeconds": 600 }, "meta": { ... } }
 * }</pre>
 *
 * <p>본문 없는 성공({@code 204})은 래퍼도 없다. 목록은 {@code data} 가 배열이다. {@code meta} 는 팩터리가 현재 요청 문맥({@link RequestIdHolder})에서 채우므로
 * 호출하는 쪽이 만들지 않는다. 오류 응답은 Controller 가 아니라 {@code GlobalExceptionHandler}·{@code ErrorResponseWriter} 가 {@link #failure} 로 만든다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error, ResponseMeta meta) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, currentMeta());
    }

    public static <T> ApiResponse<T> failure(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, currentMeta());
    }

    private static ResponseMeta currentMeta() {
        return new ResponseMeta(Instant.now().truncatedTo(ChronoUnit.MILLIS), RequestIdHolder.requestId(), RequestIdHolder.locale().getLanguage());
    }
}
