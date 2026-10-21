package com.community.zconfiguration.response;

import com.community.zconfiguration.message.RequestLocale;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 ID 를 정한다 — 요청 헤더 {@code X-Request-Id} 가 {@code [A-Za-z0-9_-]{1,64}} 이면 그대로, 없거나 틀리면 서버가 UUID 를 만든다.
 * 같은 값을 {@link RequestIdHolder}(응답 래퍼의 {@code meta.requestId}), 응답 헤더 {@code X-Request-Id}, 로그 MDC {@code requestId} 에 싣는다.
 * 보안 필터 체인보다 먼저 돌아 그 안에서 쓰는 오류 응답(401·403·503)에도 같은 값이 실린다. 끝나면 홀더와 MDC 를 반드시 비운다(스레드 재사용).
 */
class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        RequestIdHolder.set(requestId, RequestLocale.of(request));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            RequestIdHolder.clear();
        }
    }

    private static String resolve(String header) {
        return header != null && VALID.matcher(header).matches() ? header : UUID.randomUUID().toString();
    }
}
