package com.community.zconfiguration.exception;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.response.ApiResponse;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기 — 업무 예외, 입력 검증 실패, 예상하지 못한 예외를 {@link ErrorResponse} 로 바꿔 공통 응답 래퍼({@link ApiResponse#failure})에 담는다.
 *
 * <p>업무 거절은 {@link CommonException} 이 상태와 코드를 들고 오므로 핸들러 하나로 끝난다.
 * 아래 나머지는 프레임워크가 던지는 예외 중 보낸 쪽 잘못(4xx)인데 처리기가 없으면 500 으로 샜던 것들이다.
 *
 * <p>응답의 {@code message}·{@code fields} 문구는 요청 언어(Accept-Language, ko|en)로 푼다 — 예외는 문구가 아니라 메시지 키 + 인자를 들고 오고,
 * 여기서 {@link Messages} 가 푼다. 요청 스레드 안이라 로캘은 스프링의 로캘 해석({@code RequestLocaleResolver})이 정해 둔 값이다.
 *
 * <p>내부 스택트레이스·SQL·서버 경로·접속 문자열·파싱 실패 사유·허용 값 목록은 응답에 담지 않는다. 로그에만 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Messages messages;

    public GlobalExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    /** 업무 규칙에 걸린 거절. 결함이 아니라 예상한 거절이라 스택트레이스를 남기지 않는다. 대기 시간이 있는 거절은 `Retry-After` 헤더도 단다. */
    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommon(CommonException e) {
        log.info("요청 거절 [{}] {}", e.getErrorCode(), e.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.getStatus());
        if (e.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return response.body(ApiResponse.failure(ErrorResponse.of(e, messages, messages.locale())));
    }

    /**
     * {@code @Valid} 실패. 필드별로 첫 메시지만 담는다. 메시지는 DTO 의 {@code {키}} 가 Bean Validation 단계에서 이미 요청 언어로 풀려 있다.
     *
     * <p><b>순서는 결정적이다.</b> Bean Validation 은 위반을 순서 없는 집합으로 모으므로 그대로 쓰면 같은 입력인데 요청마다 {@code fields} 순서와
     * 대표 {@code message} 가 바뀐다. 그래서 요청 DTO 에 <b>선언된 필드 순서</b>로 정렬한다(칸 경로의 첫 마디 기준, 모르는 칸은 뒤에 이름순).
     * 같은 칸에 위반이 여럿이면 "비어 있음"({@code NotBlank}·{@code NotNull}·{@code NotEmpty})을 먼저, 그다음 제약 이름순으로 하나만 남긴다.
     * 대표 {@code message} 는 이렇게 정렬한 {@code fields} 의 <b>첫 문구</b>다 — 화면이 칸 순서대로 첫 칸에 초점을 주는 것과 같다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = orderedFieldMessages(e.getBindingResult());
        String first = fields.isEmpty()
                ? messages.get(new MessageRef(ErrorCode.VALIDATION.getMessageKey()))
                : fields.values().iterator().next();
        return ResponseEntity.status(ErrorCode.VALIDATION.getStatus())
                .body(ApiResponse.failure(new ErrorResponse(ErrorCode.VALIDATION.name(), first, fields, null)));
    }

    /** 위반을 DTO 선언 순서 → 칸 이름 → (비어 있음 먼저) 제약 이름 순으로 정렬해 칸마다 첫 문구만 담는다. */
    private static Map<String, String> orderedFieldMessages(BindingResult result) {
        Map<String, Integer> order = declaredOrder(result.getTarget());
        List<FieldError> sorted = new ArrayList<>(result.getFieldErrors());
        sorted.sort(Comparator
                .comparingInt((FieldError fe) -> order.getOrDefault(rootOf(fe.getField()), Integer.MAX_VALUE))
                .thenComparing(FieldError::getField)
                .thenComparingInt(fe -> isRequiredCheck(fe.getCode()) ? 0 : 1)
                .thenComparing(fe -> String.valueOf(fe.getCode())));
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : sorted) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return fields;
    }

    /** 칸 경로의 첫 마디 — {@code root.email} → {@code root}, {@code items[0].name} → {@code items} */
    private static String rootOf(String path) {
        int cut = path.length();
        for (char c : new char[] {'.', '['}) {
            int i = path.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        return path.substring(0, cut);
    }

    private static boolean isRequiredCheck(String code) {
        return "NotBlank".equals(code) || "NotNull".equals(code) || "NotEmpty".equals(code);
    }

    /** 요청 객체의 선언 순서 — 레코드는 컴포넌트 순서, 클래스는 상위 클래스부터 선언된 인스턴스 필드 순서 */
    private static Map<String, Integer> declaredOrder(Object target) {
        Map<String, Integer> order = new HashMap<>();
        if (target == null) {
            return order;
        }
        Class<?> type = target.getClass();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                order.putIfAbsent(component.getName(), order.size());
            }
            return order;
        }
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(0, c);
        }
        for (Class<?> c : chain) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    order.putIfAbsent(field.getName(), order.size());
                }
            }
        }
        return order;
    }

    /** 본문을 읽지 못했다 — 잘못된 JSON, 깨진 인코딩, 빈 본문. 보낸 쪽 잘못이라 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        // 예외 문구에는 본문 조각(비밀번호 같은 입력값)이 실릴 수 있어 로그에 남기지 않는다 — 종류만 남긴다
        log.info("요청 본문을 읽지 못했다 — {}", e.getClass().getSimpleName());
        return respond(ErrorCode.MALFORMED_REQUEST);
    }

    /** 없는 주소. 아무 매핑에도 안 걸린 요청이 정적 자원 처리까지 흘러와 던진다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.info("없는 주소 — {}", e.getResourcePath());
        return respond(ErrorCode.NOT_FOUND);
    }

    /** 쿼리·경로 값을 타입으로 못 바꿨다 (예: enum 에 없는 값). 어느 칸인지는 알려 주되 허용 값 목록은 알리지 않는다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.info("파라미터 값을 못 읽었다 — {} = {}", e.getName(), e.getValue());
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getStatus())
                .body(ApiResponse.failure(ErrorResponse.of(ErrorCode.INVALID_PARAMETER.name(),
                        messages.get("error.INVALID_PARAMETER.named", e.getName()))));
    }

    /** 안 되는 메서드 — {@code GET} 만 있는 주소에 {@code POST} 를 보낸 것처럼. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.info("안 되는 메서드 — {}", e.getMessage());
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 올린 파일이 서버 한도를 넘었다. 스프링이 multipart 를 풀다 걸리므로 서비스의 용도별 한도는 볼 기회가 없다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        log.info("업로드 크기 한도를 넘었다 — {}", e.getMessage());
        return respond(ErrorCode.FILE_TOO_LARGE);
    }

    /**
     * DB 에 붙지 못했다. 코드 결함이 아니라 DB 상태 문제라 500 이 아니라 503 이다.
     *
     * <p>커넥션을 못 얻으면 트랜잭션을 여는 자리에서 먼저 {@link CannotCreateTransactionException} 이 터지는데
     * 이것은 {@link DataAccessException} 계열이 아니라서 따로 적는다.
     */
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(Exception e) {
        log.error("DB 접근 실패", e);
        return respond(ErrorCode.DATABASE_UNAVAILABLE);
    }

    /** 예상하지 못한 실패. 원인은 응답에 담지 않고 로그에 전부 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리하지 못한 오류", e);
        return respond(ErrorCode.INTERNAL);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(ErrorResponse.of(code, messages, messages.locale())));
    }
}
