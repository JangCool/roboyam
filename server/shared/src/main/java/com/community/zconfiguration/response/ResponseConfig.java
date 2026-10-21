package com.community.zconfiguration.response;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** 공통 응답 래퍼({@link ApiResponse})가 쓰는 요청 ID 필터 등록. */
@Configuration
public class ResponseConfig {

    /**
     * 가장 앞 순서 — Content-Language 필터({@code HIGHEST_PRECEDENCE + 50})와 보안 필터 체인보다 먼저 돌아,
     * 필터·보안 단계의 오류 응답에도 요청 ID 가 실린다.
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
