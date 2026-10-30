package com.community.business.nts;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 국세청 상태조회 구현 고르기 — 배포 설정 {@code roboyam.business.nts.provider}(odcloud | fake). <b>dev·prod 프로필에서 가짜 구현이면 기동을 멈춘다</b>
 * (본인인증 {@code IdentityConfig} 와 같은 규칙) — 가짜 구현은 아무 번호나 계속사업자로 만들기 때문이다. 공개 사이트(신청 뒤 조회)와 관리자 앱(다시 조회)이 같이 쓴다.
 */
@Configuration
public class NtsConfig {

    /** 가짜 구현을 막는 프로필 */
    static final Profiles GUARDED = Profiles.of("dev", "prod");

    /** 공공데이터포털 접속·응답 제한 시간 — 신청 요청 안에서도 부르므로 짧게 */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(4);

    @Bean
    public NtsStatusClient ntsStatusClient(NtsProperties properties, Environment environment) {
        NtsProviderKind kind = guard(properties.provider(), environment);
        if (kind == NtsProviderKind.FAKE) {
            return new FakeNtsStatusClient();
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return new OdcloudNtsStatusClient(properties.serviceKey(), RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build());
    }

    /**
     * dev·prod 프로필에서 가짜 구현이면 기동을 멈춘다.
     *
     * @throws IllegalStateException 가짜 구현 + dev·prod
     */
    public static NtsProviderKind guard(NtsProviderKind kind, Environment environment) {
        if (kind == null) {
            throw new IllegalStateException("roboyam.business.nts.provider 가 비어 있습니다(odcloud | fake).");
        }
        if (kind == NtsProviderKind.FAKE && environment.acceptsProfiles(GUARDED)) {
            throw new IllegalStateException("dev·prod 프로필에서는 가짜 국세청 조회(roboyam.business.nts.provider=fake)를 쓸 수 없습니다. ROBOYAM_NTS_PROVIDER=odcloud 로 두세요.");
        }
        return kind;
    }
}
