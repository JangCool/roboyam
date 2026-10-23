package com.community.zconfiguration.exception;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Locale;
import java.util.Map;

/**
 * 오류 응답 본문. 필드 오류가 없으면 {@code fields} 는 <b>나가지 않고</b>(빈 객체도 내지 않는다 — 화면은 {@code fields} 가 없는 것으로 읽는다), {@code retryAfterSeconds} 는 해당될 때만 나간다.
 * {@code remainingAttempts}(잠기기 전 남은 시도 횟수 — 관리자 2차 인증 코드 {@code MFA_CODE_INVALID})도 해당될 때만 나간다.
 *
 * <pre>{@code { "code": "VALIDATION", "message": "...", "fields": { "root.email": "..." }, "retryAfterSeconds": 600 } }</pre>
 *
 * <p>{@code message}·{@code fields} 의 문구는 <b>요청 언어로 푼 문구</b>다({@link Messages}). {@code code}·{@code fields} 의 칸 경로 키는 언어와 무관하다.
 * {@code fields} 에 여러 필드를 담는 것은 화면이 필드마다 메시지를 아래에 붙여야 하기 때문이다.
 * 키 이름 {@code code} 는 화면의 {@code useApiClient} 가 읽는 이름이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fields, Long retryAfterSeconds,
                            Integer remainingAttempts) {

    /** 남은 시도 횟수가 없는 오류 */
    public ErrorResponse(String code, String message, Map<String, String> fields, Long retryAfterSeconds) {
        this(code, message, fields, retryAfterSeconds, null);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of(), null);
    }

    public static ErrorResponse of(ErrorCode code, Messages messages, Locale locale) {
        return of(code.name(), messages.get(new MessageRef(code.getMessageKey()), locale));
    }

    public static ErrorResponse of(CommonException e, Messages messages, Locale locale) {
        return new ErrorResponse(e.getErrorCode(), messages.get(e.getMessageRef(), locale), messages.get(e.getFields(), locale),
                e.getRetryAfterSeconds(), e.getRemainingAttempts());
    }
}
