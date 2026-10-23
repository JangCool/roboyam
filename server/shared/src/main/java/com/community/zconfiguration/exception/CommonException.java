package com.community.zconfiguration.exception;

import com.community.zconfiguration.message.MessageRef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 업무 규칙에 걸린 실패. 결함이 아니라 예상한 거절이다.
 *
 * <p>HTTP 상태와 오류 코드를 예외가 들고 오므로 {@link GlobalExceptionHandler} 의 핸들러는 하나로 끝나고,
 * 새 예외를 만들 때 핸들러를 고칠 일이 없다. 도메인 예외는 각 업무의 {@code exception} 패키지에서 이것을 상속한다.
 *
 * <p>사람이 읽는 문구는 예외가 들고 있지 않다 — <b>메시지 키 + 인자</b>({@link MessageRef})만 들고 오고, 응답을 만드는 자리
 * ({@link GlobalExceptionHandler}·{@link ErrorResponseWriter})가 요청 언어로 푼다. 키는 {@code error.<오류 코드>} 로 짓는다.
 *
 * <pre>{@code
 * public class MemberNotFoundException extends CommonException {
 *     public MemberNotFoundException() {
 *         super(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", new MessageRef("error.MEMBER_NOT_FOUND"));
 *     }
 * }
 * }</pre>
 *
 * <p>스택트레이스를 남기지 않는다({@code writableStackTrace = false}). 거절은 결함이 아니므로 남길 이유가 없다.
 */
public class CommonException extends RuntimeException {

    /** 이 거절을 어떤 HTTP 상태로 낼 것인가. */
    private final HttpStatus status;

    /** 화면이 분기에 쓰는 코드. 사람이 읽는 {@code message} 와 달리 문구·언어를 바꿔도 유지된다. */
    private final String errorCode;

    /** 응답의 {@code message} 가 될 문구(키 + 인자) */
    private final MessageRef messageRef;

    /** 입력칸별 오류 문구(점·대괄호 경로 키 → 문구 키 + 인자). 경로 키는 언어와 무관하다. 없으면 빈 맵 */
    private final Map<String, MessageRef> fields;

    /** 다시 시도할 수 있기까지의 초(응답 본문과 {@code Retry-After} 헤더로 나간다). 없으면 null */
    private final Long retryAfterSeconds;

    /** 잠기기 전까지 남은 시도 횟수(응답 본문의 {@code remainingAttempts} — 관리자 2차 인증 코드처럼 화면이 「N번 남았어요」를 보일 때). 없으면 null */
    private final Integer remainingAttempts;

    protected CommonException(HttpStatus status, String errorCode, MessageRef message) {
        this(status, errorCode, message, Map.of(), null);
    }

    protected CommonException(HttpStatus status, String errorCode, MessageRef message, Map<String, MessageRef> fields,
                              Long retryAfterSeconds) {
        this(status, errorCode, message, fields, retryAfterSeconds, null);
    }

    protected CommonException(HttpStatus status, String errorCode, MessageRef message, Map<String, MessageRef> fields,
                              Long retryAfterSeconds, Integer remainingAttempts) {
        super(message.key(), null, false, false); // 로그용 — 응답 문구는 messageRef 를 요청 언어로 풀어 만든다
        this.status = status;
        this.errorCode = errorCode;
        this.messageRef = message;
        this.fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields)); // 검사 순서를 지킨다
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingAttempts = remainingAttempts;
    }

    public Integer getRemainingAttempts() {
        return remainingAttempts;
    }

    public CommonException(ErrorCode code) {
        this(code.getStatus(), code.name(), new MessageRef(code.getMessageKey()));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public MessageRef getMessageRef() {
        return messageRef;
    }

    public Map<String, MessageRef> getFields() {
        return fields;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
