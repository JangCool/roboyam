package com.community.zconfiguration.security;

import com.community.zconfiguration.StaticAssetUrls;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 화면(HTML)·API 응답에서 CSRF 토큰을 꺼내 쿠키(XSRF-TOKEN)가 내려가게 한다(지연 로딩되는 토큰을 강제로 읽는다). 공개 사이트와 관리자 앱이 함께 쓴다.
 *
 * <p><b>정적 파일 응답에는 붙이지 않는다</b>({@link #STATIC_PREFIXES} — 빌드 조각 {@code /_nuxt/}, 앱에 딸린 그림, 올린 파일 {@code /media/}).
 * 쿠키가 없는 방문자의 응답마다 {@code Set-Cookie} 가 붙으면 CDN(Cloudflare)이 그 응답을 캐시하지 않아, JS·CSS·그림을 매번 원서버에서 받았다(운영 측정 2026-11-14 — 전부 BYPASS).
 * 화면은 HTML·첫 API 응답에서 쿠키를 받으므로 동작은 같다.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    /** 쿠키를 붙이지 않는 정적 파일 자리(앞 경로) */
    static final List<String> STATIC_PREFIXES = Stream.concat(Stream.of("/_nuxt/", "/media/"), StaticAssetUrls.FOLDERS.stream()).toList();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return STATIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

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
