package com.community.zconfiguration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** 모든 응답에서 CSRF 토큰을 꺼내 쿠키(XSRF-TOKEN)가 내려가게 한다(지연 로딩되는 토큰을 강제로 읽는다). 공개 사이트와 관리자 앱이 함께 쓴다 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken(); // 쿠키 저장소가 이때 XSRF-TOKEN 쿠키를 쓴다
        }
        filterChain.doFilter(request, response);
    }
}
