package com.community.site.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * 시험에서 화면 주소 요청을 {@link SitePageHandler} 로 — {@code SitePageRoutes} 와 같은 조건(GET·HEAD + {@link SitePageHandler#isPage})일 때만 처리기를 부르고 응답을 써 넣는다.
 * 조건에 맞지 않으면 아무것도 하지 않는다(실제로는 API 컨트롤러·정적 파일이 받는다).
 */
public final class SitePageTestSupport {

    private static final List<HttpMessageConverter<?>> CONVERTERS = List.of(new ByteArrayHttpMessageConverter());

    private SitePageTestSupport() {
    }

    public static void serve(SitePageHandler handler, HttpServletRequest request, HttpServletResponse response) throws Exception {
        boolean method = HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
        if (!method || !SitePageHandler.isPage(request.getServletPath())) {
            return;
        }
        ServerResponse out = handler.page(ServerRequest.create(request, CONVERTERS));
        out.writeTo(request, response, () -> CONVERTERS);
    }
}
