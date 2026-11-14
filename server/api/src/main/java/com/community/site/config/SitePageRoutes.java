package com.community.site.config;

import com.community.site.controller.SitePageHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * 화면(SPA) 주소의 길 — {@code GET}·{@code HEAD} 이고 화면 주소({@link SitePageHandler#isPage} — 확장자가 없는 주소와 {@code /index.html})면 {@link SitePageHandler#page}.
 *
 * <p>MVC 함수형 길(RouterFunction)을 쓰는 까닭: 화면 주소는 「확장자가 없는 모든 주소」라 {@code @GetMapping} 주소 패턴으로 적을 수 없다({@code /{*path}} 로 다 받으면
 * {@code /_nuxt/*.js} 같은 정적 파일까지 가로챈다). 조건(술어)으로 고르면 API 컨트롤러({@code /api/**} — 먼저 판단)와 정적 파일(나중 판단 — {@code SpaResourceConfig}) 사이에서
 * 화면 주소만 받는다. 다른 API 처럼 DispatcherServlet 안이라 예외 처리·로그가 같다.
 */
@Configuration
public class SitePageRoutes {

    @Bean
    public RouterFunction<ServerResponse> sitePages(SitePageHandler handler) {
        return RouterFunctions.route(
                RequestPredicates.method(HttpMethod.GET).or(RequestPredicates.method(HttpMethod.HEAD)).and(request -> SitePageHandler.isPage(request.path())),
                handler::page);
    }
}
