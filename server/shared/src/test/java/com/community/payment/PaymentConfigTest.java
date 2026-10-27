package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.identity.config.IdentityProperties;
import com.community.identity.dto.IdentityProviderKind;
import com.community.payment.config.PaymentConfig;
import com.community.payment.config.PaymentProperties;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.gateway.portone.PortOnePaymentGateway;
import com.community.payment.testorder.PaymentTestOrderHandler;
import com.community.portone.repository.master.PortOneSettingStore;
import com.community.portone.service.PortOneSettingsService;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 결제 게이트웨이 고르기·기동 막기(계약 결정 1·7, commerce.md 8-5) — dev·prod 에서 가짜 게이트웨이면 기동 실패, prod 에서 시험 주문을 켜면 기동 실패, 공통 기본값은 포트원·시험 주문 꺼짐,
 * local 만 가짜·시험 주문. 숫자 설정(결정 18)의 기본값.
 */
class PaymentConfigTest {

    private static MockEnvironment profiles(String... active) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(active);
        return environment;
    }

    private static PaymentProperties properties(PaymentGatewayKind kind, boolean testOrders) {
        PaymentProperties d = PaymentProperties.DEFAULTS;
        return new PaymentProperties(kind, testOrders, d.cancelBatchSize(), d.cancelGap(), d.cancelMaxRetries(), d.cancelBackoff(), d.cancelStale());
    }

    private static PaymentGateway build(PaymentGatewayKind kind, boolean testOrders, String... active) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("portOneSettingsService", new PortOneSettingsService(new PortOneSettingStore(dataSource), new SecretEncryptor(""),
                new IdentityProperties(IdentityProviderKind.PORTONE, Duration.ofMinutes(30)), com.community.payment.config.PaymentProperties.DEFAULTS, null, new StaticListableBeanFactory().getBeanProvider(Clock.class)));
        return new PaymentConfig().paymentGateway(properties(kind, testOrders), profiles(active), dataSource, new DataSourceTransactionManager(dataSource),
                beans.getBeanProvider(PortOneSettingsService.class), beans.getBeanProvider(Clock.class));
    }

    @Test
    void dev_prod_에서_가짜_게이트웨이면_기동을_멈춘다() {
        for (String profile : new String[] {"dev", "prod"}) {
            assertThatThrownBy(() -> build(PaymentGatewayKind.FAKE, false, profile)).as(profile).isInstanceOf(IllegalStateException.class).hasMessageContaining("fake");
            assertThatThrownBy(() -> build(PaymentGatewayKind.FAKE, false, "local", profile)).as("local+" + profile).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void prod_에서_시험_주문을_켜면_기동을_멈추고_dev_local_은_된다() {
        assertThatThrownBy(() -> build(PaymentGatewayKind.PORTONE, true, "prod")).isInstanceOf(IllegalStateException.class).hasMessageContaining("test-orders");
        assertThat(build(PaymentGatewayKind.PORTONE, true, "dev")).isInstanceOf(PortOnePaymentGateway.class);
        assertThat(build(PaymentGatewayKind.FAKE, true, "local")).isInstanceOf(FakePaymentGateway.class);
    }

    @Test
    void local_과_시험은_가짜를_쓸_수_있고_포트원은_어디서나_된다() {
        assertThat(build(PaymentGatewayKind.FAKE, false, "local")).isInstanceOf(FakePaymentGateway.class);
        assertThat(build(PaymentGatewayKind.FAKE, false)).isInstanceOf(FakePaymentGateway.class);
        assertThat(build(PaymentGatewayKind.PORTONE, false, "prod")).isInstanceOf(PortOnePaymentGateway.class);
        assertThat(build(PaymentGatewayKind.PORTONE, false, "prod").ready()).as("설정이 비면 준비 안 됨 — 앱은 뜬다").isFalse();
        assertThatThrownBy(() -> PaymentConfig.guard(properties(null, false), profiles("local"))).isInstanceOf(IllegalStateException.class);
    }

    @Configuration
    @EnableConfigurationProperties(PaymentProperties.class)
    static class PropertiesOnly {
    }

    @Test
    void 숫자_설정의_기본값은_20건_200ms_재시도_5회_1_5_30_120_720분_10분() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class).run(context -> {
            PaymentProperties bound = context.getBean(PaymentProperties.class);
            assertThat(bound.gateway()).isEqualTo(PaymentGatewayKind.PORTONE);
            assertThat(bound.testOrders()).isFalse();
            assertThat(bound.cancelBatchSize()).isEqualTo(20);
            assertThat(bound.cancelGap()).isEqualTo(Duration.ofMillis(200));
            assertThat(bound.cancelMaxRetries()).isEqualTo(5);
            assertThat(bound.cancelMaxAttempts()).isEqualTo(6);
            assertThat(bound.cancelBackoff()).containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofMinutes(120),
                    Duration.ofMinutes(720));
            assertThat(bound.cancelStale()).isEqualTo(Duration.ofMinutes(10));
            assertThat(bound).isEqualTo(PaymentProperties.DEFAULTS);
        });
        new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class).withPropertyValues("roboyam.payment.cancel-max-retries=6")
                .run(context -> assertThat(context).as("재시도 간격이 횟수보다 적다").hasFailed());
    }

    @Test
    void 시험_주문_처리기는_test_orders_를_켠_서버에만_있다() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PaymentTestOrderHandler.class);
        runner.run(context -> assertThat(context).doesNotHaveBean(PaymentTestOrderHandler.class));
        runner.withPropertyValues("roboyam.payment.test-orders=true").run(context -> assertThat(context).hasSingleBean(PaymentTestOrderHandler.class));
    }

    private static Properties yaml(String path) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(path));
        return factory.getObject();
    }

    @Test
    void 공통_설정은_포트원_시험_주문_꺼짐이고_local_만_가짜와_시험_주문이다() {
        Properties shared = yaml("roboyam-shared.yml");
        assertThat(shared.getProperty("roboyam.payment.gateway")).isEqualTo("${ROBOYAM_PAYMENT_GATEWAY:portone}");
        assertThat(shared.getProperty("roboyam.payment.test-orders")).isEqualTo("${ROBOYAM_PAYMENT_TEST_ORDERS:false}");
        assertThat(shared.getProperty("roboyam.payment.cancel-batch-size")).isEqualTo("20");
        assertThat(shared.getProperty("roboyam.payment.cancel-max-retries")).isEqualTo("5");
        assertThat(shared.getProperty("roboyam.payment.cancel-backoff")).isEqualTo("1m,5m,30m,120m,720m");
        Properties local = yaml("roboyam-shared-local.yml");
        assertThat(local.getProperty("roboyam.payment.gateway")).isEqualTo("${ROBOYAM_PAYMENT_GATEWAY:fake}");
        assertThat(local.getProperty("roboyam.payment.test-orders")).isEqualTo("${ROBOYAM_PAYMENT_TEST_ORDERS:true}");
        assertThat(List.of(FakePaymentGateway.STORE_ID, FakePaymentGateway.WEBHOOK_SECRET)).allSatisfy(value -> assertThat(value).isNotBlank());
    }
}
