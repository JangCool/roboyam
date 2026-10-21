package com.community.zconfiguration.message;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** {@code /api/**} 응답에 요청 언어({@code Content-Language})를 싣는다. 보안 필터보다 먼저 돌아 필터가 쓰는 오류 응답에도 실린다. */
class ContentLanguageFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Language", RequestLocale.of(request).getLanguage());
        response.addHeader("Vary", "Accept-Language");
        chain.doFilter(request, response);
    }
}
