package com.community.holiday.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 특일 정보 HTTP({@link SpecialDayApi}) — 주소는 호출마다 설정에서 읽으므로 기본 주소 없이 만든다. 배치·운영자 「지금 받기」·연결 시험만 부르므로(회원 요청 안이 아니다)
 * 국세청보다 길게 기다린다. 다른 곳으로 넘기지 않는다(redirect 막음).
 */
@Configuration
public class HolidayClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public SpecialDayApi specialDayApi() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return new SpecialDayApi(RestClient.builder().requestFactory(factory).build());
    }
}
