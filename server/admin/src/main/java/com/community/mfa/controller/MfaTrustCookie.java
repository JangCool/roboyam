package com.community.mfa.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 신뢰 기기 쿠키 {@code ADMIN_MFA_TRUST} 읽기·쓰기 — 웹 계층의 도우미다(서비스는 쿠키를 모른다).
 * HttpOnly · SameSite=Strict · https 요청이면 Secure(프록시의 {@code X-Forwarded-Proto} 를 따른다) · path {@code /api/admin} · 기한 = 신뢰 일수.
 * 값(256비트 무작위)을 로그에 싣지 않는다 — DB 에는 해시만 있다.
 */
@Component
public class MfaTrustCookie {

    public static final String NAME = "ADMIN_MFA_TRUST";
    static final String PATH = "/api/admin";

    /** 요청의 쿠키 값(없으면 null) */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, String token, int trustDays) {
        response.addHeader(HttpHeaders.SET_COOKIE, base(request, token).maxAge(Duration.ofDays(trustDays)).build().toString());
    }

    /** 쿠키를 지운다(끊은 기기가 이 브라우저였을 때) */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, base(request, "").maxAge(Duration.ZERO).build().toString());
    }

    private static ResponseCookie.ResponseCookieBuilder base(HttpServletRequest request, String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(request.isSecure()).sameSite("Strict").path(PATH);
    }
}
