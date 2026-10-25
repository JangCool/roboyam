package com.community.identity.config;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.provider.FakeIdentityVerifier;
import com.community.identity.provider.IdentityVerifier;
import com.community.identity.provider.PortOneIdentityVerifier;
import com.community.portone.service.PortOneSettingsService;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 본인인증 구현 고르기 — 배포 설정 {@code roboyam.identity.provider}(portone | fake). <b>dev·prod 프로필에서 가짜 구현이면 기동을 멈춘다</b>(commerce.md 12-1 「결제 Fake 와 같다」,
 * 세션 정리 결정 9) — 가짜 구현은 아무 생년월일·사람이나 인증된 것으로 만들기 때문이다. 공개 사이트와 관리자 앱이 같이 쓴다(관리자 앱은 포트원 설정 화면에 지금 구현을 보인다).
 */
@Configuration
public class IdentityConfig {

    /** 가짜 구현을 막는 프로필 */
    static final Profiles GUARDED = Profiles.of("dev", "prod");

    /** 포트원 접속·응답 제한 시간 */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public IdentityVerifier identityVerifier(IdentityProperties properties, Environment environment, ObjectProvider<PortOneSettingsService> settings,
                                             ObjectProvider<Clock> clocks) {
        IdentityProviderKind kind = guard(properties.provider(), environment);
        if (kind == IdentityProviderKind.FAKE) {
            return new FakeIdentityVerifier(clocks.getIfUnique(Clock::systemUTC));
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(READ_TIMEOUT);
        PortOneSettingsService portOne = settings.getObject();
        return new PortOneIdentityVerifier(portOne::identityCredentials, RestClient.builder().requestFactory(factory));
    }

    /**
     * dev·prod 프로필에서 가짜 구현이면 기동을 멈춘다.
     *
     * @return 고른 구현
     * @throws IllegalStateException 가짜 구현 + dev·prod
     */
    public static IdentityProviderKind guard(IdentityProviderKind kind, Environment environment) {
        if (kind == null) {
            throw new IllegalStateException("roboyam.identity.provider 가 비어 있습니다(portone | fake).");
        }
        if (kind == IdentityProviderKind.FAKE && environment.acceptsProfiles(GUARDED)) {
            throw new IllegalStateException("dev·prod 프로필에서는 가짜 본인인증(roboyam.identity.provider=fake)을 쓸 수 없습니다. ROBOYAM_IDENTITY_PROVIDER=portone 으로 두세요.");
        }
        return kind;
    }
}
