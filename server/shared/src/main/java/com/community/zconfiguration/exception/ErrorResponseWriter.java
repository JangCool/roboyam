package com.community.zconfiguration.exception;

import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.message.RequestLocale;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 컨트롤러 밖(보안 필터: 설치 전 가드·401·403·CSRF)에서 오류 응답을 쓰는 도우미.
 * {@link GlobalExceptionHandler} 와 같은 JSON(공통 응답 래퍼 {@link ApiResponse#failure} 안에 {@link ErrorResponse})을 만든다 — 상태·코드·문구는 컨트롤러에서 던지는 것과 같은 예외가 들고 있다.
 *
 * <p>필터는 DispatcherServlet 보다 먼저 돌아 스프링의 로캘 해석을 기다릴 수 없으므로 요청의 {@code Accept-Language} 를 {@link RequestLocale} 로 직접 읽는다
 * (컨트롤러 쪽과 같은 규칙).
 */
@Component
public class ErrorResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponseWriter.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Messages messages;

    public ErrorResponseWriter(Messages messages) {
        this.messages = messages;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, CommonException e) throws IOException {
        log.info("요청 거절 [{}] {}", e.getErrorCode(), e.getMessage());
        Locale locale = RequestLocale.of(request);
        response.setStatus(e.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Language", locale.getLanguage());
        response.setHeader("Cache-Control", "no-store");
        if (e.getRetryAfterSeconds() != null) {
            response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        }
        response.getOutputStream().write(MAPPER.writeValueAsBytes(ApiResponse.failure(ErrorResponse.of(e, messages, locale))));
    }
}
