package com.community.groupbuy.tracking;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 송장 조회 구현(결정 90) — 스마트택배 조회 API. 기본 주소는 배포 설정 {@code irobit.commerce.tracking.base-url}(기본 {@code https://info.sweettracker.co.kr}).
 * 배치·[지금 조회]가 부르므로 제한 시간을 짧게(접속 3초 · 응답 5초), 리다이렉트는 따라가지 않는다. 공구 시험은 이 설정을 빼고 가짜 구현을 넣는다.
 */
@Configuration
public class ParcelTrackingConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public ParcelTrackingClient parcelTrackingClient(@Value("${irobit.commerce.tracking.base-url:https://info.sweettracker.co.kr}") String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return new SweetTrackerClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build());
    }
}
