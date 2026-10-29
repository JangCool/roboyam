package com.community.address.config;

import com.community.zconfiguration.exception.ErrorResponseWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.unit.DataSize;

/** 주소 DB 올리기만 큰 파일을 받게 하는 필터 등록 — 요청 ID 필터 뒤, 보안 필터 체인 앞(본문을 읽기 전에 거른다) */
@Configuration
public class AddressUploadConfig {

    @Bean
    public FilterRegistrationBean<AddressUploadLimitFilter> addressUploadLimitFilter(@Value("${roboyam.upload.default-max-request-size}") DataSize defaultMax,
                                                                                    ErrorResponseWriter errors) {
        FilterRegistrationBean<AddressUploadLimitFilter> registration = new FilterRegistrationBean<>(new AddressUploadLimitFilter(defaultMax, errors));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 60);
        return registration;
    }
}
