package com.community.zconfiguration.exception;

import com.community.zconfiguration.message.MessageRef;
import java.util.Map;

/**
 * 입력이 규칙에 맞지 않는다(400 {@code VALIDATION}). 입력칸별 문구는 {@code fields} 로 나가 화면이 칸 바로 아래에 붙인다.
 * 칸 경로 키(점·대괄호)는 언어와 무관하고, 문구는 키 + 인자({@link MessageRef})로 들고 있다가 응답을 만들 때 요청 언어로 풀린다.
 *
 * <p>서비스가 위반을 한꺼번에 모아 던지는 곳(설치 마법사처럼 검사 순서가 계약이고 점·대괄호 경로 키가 필요한 곳)이 쓴다.
 * 단순한 요청은 Bean Validation({@code @Valid})이 {@link GlobalExceptionHandler} 에서 같은 응답으로 바뀐다.
 */
public class ValidationException extends CommonException {

    public ValidationException(Map<String, MessageRef> fields) {
        super(ErrorCode.VALIDATION.getStatus(), ErrorCode.VALIDATION.name(), new MessageRef(ErrorCode.VALIDATION.getMessageKey()), fields, null);
    }

    public static ValidationException of(String field, MessageRef message) {
        return new ValidationException(Map.of(field, message));
    }
}
