package com.community.zconfiguration.message;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 서버가 응답에 싣는 문구의 다국어 설정(지원 언어 {@code ko}·{@code en}, 기본 {@code ko}).
 * 번들 위치·인코딩은 {@code roboyam-shared.yml} 의 {@code spring.messages.*} 이다.
 *
 * <ul>
 *   <li>{@code messageSource}: 해당 언어에 키가 없으면 {@code ko} 로 되돌린다(시스템 로캘로 되돌리지 않는다). 이 빈이 있으므로 Bean Validation 의
 *       {@code {키}} 메시지도 같은 번들에서 풀린다.</li>
 *   <li>{@code localeResolver}: 요청 언어를 {@code Accept-Language} 로 정한다({@link RequestLocale}).</li>
 *   <li>{@code Content-Language}: {@code /api/**} 응답에 요청 언어를 싣는다.</li>
 * </ul>
 */
@Configuration
public class MessageConfig {

    /** 번들 기본 위치(클래스패스, 확장자·언어 제외) */
    static final String DEFAULT_BASENAME = "messages/messages";

    @Bean
    public MessageSource messageSource(@Value("${spring.messages.basename:" + DEFAULT_BASENAME + "}") String basename,
                                       @Value("${spring.messages.encoding:UTF-8}") String encoding,
                                       @Value("${spring.messages.fallback-to-system-locale:false}") boolean fallbackToSystemLocale) {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(basename);
        source.setDefaultEncoding(encoding);
        source.setFallbackToSystemLocale(fallbackToSystemLocale);
        source.setDefaultLocale(Locale.KOREAN); // 키가 없는 언어는 ko 로
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new RequestLocaleResolver();
    }

    @Bean
    public FilterRegistrationBean<ContentLanguageFilter> contentLanguageFilter() {
        FilterRegistrationBean<ContentLanguageFilter> registration = new FilterRegistrationBean<>(new ContentLanguageFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50); // 보안 필터 체인보다 앞
        return registration;
    }
}
