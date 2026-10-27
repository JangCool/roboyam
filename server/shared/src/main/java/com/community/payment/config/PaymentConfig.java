package com.community.payment.config;

import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.gateway.fake.FakePgStore;
import com.community.payment.gateway.portone.PortOnePaymentGateway;
import com.community.portone.service.PortOneSettingsService;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * 결제 게이트웨이 고르기 — 배포 설정 {@code roboyam.payment.gateway}(portone | fake, 결정 1). {@code IdentityConfig.guard} 와 같은 모양으로 기동을 막는다:
 * <ul>
 *   <li><b>dev·prod 프로필에서 가짜 게이트웨이면 멈춘다</b>(commerce.md 8-5) — 가짜는 아무 결제나 「결제됨」으로 만든다.</li>
 *   <li><b>prod 프로필에서 시험 주문({@code roboyam.payment.test-orders})을 켜면 멈춘다</b>(결정 7) — 시험 주문은 도메인 없이 돈을 받는 길이다.</li>
 * </ul>
 * 공개 사이트(완료 API·웹훅)와 관리자 앱(취소 워커·READY 정리)이 같이 쓴다.
 */
@Configuration
public class PaymentConfig {

    /** 가짜 게이트웨이를 막는 프로필 */
    static final Profiles FAKE_GUARDED = Profiles.of("dev", "prod");
    /** 시험 주문을 막는 프로필 */
    static final Profiles TEST_ORDERS_GUARDED = Profiles.of("prod");

    /** 포트원 접속·응답 제한 시간 */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public PaymentGateway paymentGateway(PaymentProperties properties, Environment environment, DataSource dataSource, PlatformTransactionManager transactions,
                                         ObjectProvider<PortOneSettingsService> settings, ObjectProvider<Clock> clocks) {
        PaymentGatewayKind kind = guard(properties, environment);
        if (kind == PaymentGatewayKind.FAKE) {
            // 가짜 PG 의 취소는 우리 업무 트랜잭션과 따로 확정한다 — 실제 PG 처럼 우리 쪽이 되돌려져도 취소는 남는다(재시도 전 조회가 맞춘다)
            TransactionTemplate separate = new TransactionTemplate(transactions);
            separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return new FakePaymentGateway(new FakePgStore(dataSource), separate, clocks.getIfUnique(Clock::systemUTC));
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(READ_TIMEOUT);
        PortOneSettingsService portOne = settings.getObject();
        return new PortOnePaymentGateway(portOne::paymentCredentials, RestClient.builder().requestFactory(factory));
    }

    /**
     * 기동 검사 — dev·prod 의 가짜 게이트웨이, prod 의 시험 주문을 막는다.
     *
     * @return 고른 게이트웨이
     * @throws IllegalStateException 막는 조합일 때
     */
    public static PaymentGatewayKind guard(PaymentProperties properties, Environment environment) {
        if (properties.gateway() == null) {
            throw new IllegalStateException("roboyam.payment.gateway 가 비어 있습니다(portone | fake).");
        }
        if (properties.gateway() == PaymentGatewayKind.FAKE && environment.acceptsProfiles(FAKE_GUARDED)) {
            throw new IllegalStateException("dev·prod 프로필에서는 가짜 결제 게이트웨이(roboyam.payment.gateway=fake)를 쓸 수 없습니다. ROBOYAM_PAYMENT_GATEWAY=portone 으로 두세요.");
        }
        if (properties.testOrders() && environment.acceptsProfiles(TEST_ORDERS_GUARDED)) {
            throw new IllegalStateException("prod 프로필에서는 결제 시험 주문(roboyam.payment.test-orders=true)을 켤 수 없습니다.");
        }
        return properties.gateway();
    }
}
