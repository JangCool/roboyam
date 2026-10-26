package com.community.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.provider.FakeIdentityVerifier;
import com.community.identity.provider.IdentityVerifier;
import com.community.identity.provider.PortOneIdentityVerifier;
import com.community.portone.service.PortOneSettingsService;
import java.time.Clock;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 본인인증 구현 고르기(세션 정리 결정 9, commerce.md 12-1) — dev·prod 프로필에서 가짜 구현이면 기동을 멈춘다. 공통 설정의 기본값은 포트원, local 만 가짜.
 */
class IdentityConfigTest {

    private static MockEnvironment profiles(String... active) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(active);
        return environment;
    }

    /** 만들기만 하고 부르지 않는 포트원 설정(DB 에 붙지 않는다) */
    private static PortOneSettingsService unusedPortOneSettings() {
        return new PortOneSettingsService(new com.community.portone.repository.master.PortOneSettingStore(new org.springframework.jdbc.datasource.DriverManagerDataSource()),
                new com.community.zconfiguration.crypto.SecretEncryptor(""), new IdentityProperties(IdentityProviderKind.PORTONE, Duration.ofMinutes(30)), null,
                new StaticListableBeanFactory().getBeanProvider(Clock.class));
    }

    private static IdentityVerifier build(IdentityProviderKind kind, String... active) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("portOneSettingsService", unusedPortOneSettings());
        return new IdentityConfig().identityVerifier(new IdentityProperties(kind, Duration.ofMinutes(30)), profiles(active),
                beans.getBeanProvider(PortOneSettingsService.class), beans.getBeanProvider(Clock.class));
    }

    @Test
    void dev_prod_에서_가짜_구현이면_기동을_멈춘다() {
        for (String profile : new String[] {"dev", "prod"}) {
            assertThatThrownBy(() -> build(IdentityProviderKind.FAKE, profile)).as(profile).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("fake");
            assertThatThrownBy(() -> build(IdentityProviderKind.FAKE, "local", profile)).as("local+" + profile).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void local_과_시험은_가짜_구현을_쓸_수_있고_포트원은_어디서나_된다() {
        assertThat(build(IdentityProviderKind.FAKE, "local")).isInstanceOf(FakeIdentityVerifier.class);
        assertThat(build(IdentityProviderKind.FAKE)).isInstanceOf(FakeIdentityVerifier.class);
        assertThat(build(IdentityProviderKind.PORTONE, "prod")).isInstanceOf(PortOneIdentityVerifier.class);
        assertThat(build(IdentityProviderKind.PORTONE, "dev")).isInstanceOf(PortOneIdentityVerifier.class);
        assertThatThrownBy(() -> IdentityConfig.guard(null, profiles("local"))).isInstanceOf(IllegalStateException.class);
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(IdentityProperties.class)
    static class PropertiesOnly {
    }

    private static org.springframework.boot.test.context.runner.ApplicationContextRunner runner(String provider, String... profiles) {
        return new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(PropertiesOnly.class, IdentityConfig.class)
                .withPropertyValues("roboyam.identity.provider=" + provider)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles));
    }

    @Test
    void 스프링_문맥으로도_dev_prod_가짜면_뜨지_않고_local_은_뜬다() {
        runner("fake", "prod").run(context -> assertThat(context).hasFailed());
        runner("fake", "dev").run(context -> assertThat(context).hasFailed());
        runner("fake", "local").run(context -> assertThat(context).hasNotFailed().getBean(IdentityVerifier.class).isInstanceOf(FakeIdentityVerifier.class));
        runner("FAKE").run(context -> assertThat(context).hasNotFailed());
    }

    private static Properties yaml(String path) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(path));
        return factory.getObject();
    }

    @Test
    void 공통_설정의_기본값은_포트원이고_local_만_가짜다_인증_번호는_30분() {
        Properties shared = yaml("roboyam-shared.yml");
        assertThat(shared.getProperty("roboyam.identity.provider")).isEqualTo("${ROBOYAM_IDENTITY_PROVIDER:portone}");
        assertThat(shared.getProperty("roboyam.identity.verification-ttl")).isEqualTo("30m");
        assertThat(yaml("roboyam-shared-local.yml").getProperty("roboyam.identity.provider")).isEqualTo("${ROBOYAM_IDENTITY_PROVIDER:fake}");
        assertThat(shared.getProperty("roboyam.withdrawal.rejoin-block-days")).isEqualTo("30");
        assertThat(shared.getProperty("roboyam.withdrawal.contact-retention-years")).isEqualTo("5");
    }
}
