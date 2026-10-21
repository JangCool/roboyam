package com.community.zconfiguration.message;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.web.servlet.LocaleResolver;

/** 스프링 MVC 가 요청 언어를 정하는 방법 — {@link RequestLocale} 의 규칙(Accept-Language, {@code ko}·{@code en}, 기본 {@code ko}) 그대로. */
public class RequestLocaleResolver implements LocaleResolver {

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return RequestLocale.of(request);
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("언어는 요청 헤더(Accept-Language)에서만 정한다");
    }
}
