package com.community.payment.config;

import com.community.payment.gateway.PaymentGatewayKind;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.payment.* — 결제 모듈의 배포 설정(계약 결정 1·7·18). 운영 정책 값(READY 여유 시간·웹훅 기록 보관)은 {@code batch_params}, 포트원 키는 관리자 화면의 DB 설정이다.
 *
 * @param gateway         결제 게이트웨이 — {@code portone} · {@code fake}(local·시험 전용, <b>dev·prod 프로필에서는 기동을 멈춘다</b>). 환경 변수 {@code ROBOYAM_PAYMENT_GATEWAY}.
 *                        공통 기본값 portone, local 은 fake
 * @param testOrders      시험 주문({@code PAYMENT_TEST} + {@code /api/dev/payments})을 켤까 — local·dev 만, <b>prod 프로필에서 켜면 기동을 멈춘다</b>(결정 7). 기본 꺼짐
 * @param cancelBatchSize 취소 워커가 한 바퀴에 집는 줄 수(기본 20 — PG 호출 속도 제한, commerce.md 8-3)
 * @param cancelGap       취소 워커의 건 사이 간격(기본 200ms)
 * @param cancelMaxRetries 즉시 시도 뒤 재시도 횟수(기본 5 — D10 「즉시 시도 + 재시도 최대 5회」). 다 쓰면 GAVE_UP
 * @param cancelBackoff   n 번째 재시도까지의 간격(기본 1·5·30·120·720분) — 재시도 횟수만큼 있어야 한다
 * @param cancelStale     SENDING 으로 이만큼 머문 취소는 워커가 죽은 것으로 보고 PENDING 으로 되돌린다(기본 10분 — 재시도 전 조회가 이중 취소를 막는다)
 */
@ConfigurationProperties(prefix = "roboyam.payment")
public record PaymentProperties(@DefaultValue("portone") PaymentGatewayKind gateway, @DefaultValue("false") boolean testOrders,
                                @DefaultValue("20") int cancelBatchSize, @DefaultValue("200ms") Duration cancelGap, @DefaultValue("5") int cancelMaxRetries,
                                @DefaultValue({"1m", "5m", "30m", "120m", "720m"}) List<Duration> cancelBackoff, @DefaultValue("10m") Duration cancelStale) {

    /** 기본값(시험이 손으로 만들 때) */
    public static final PaymentProperties DEFAULTS = new PaymentProperties(PaymentGatewayKind.PORTONE, false, 20, Duration.ofMillis(200), 5,
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofMinutes(120), Duration.ofMinutes(720)), Duration.ofMinutes(10));

    public PaymentProperties {
        cancelBackoff = cancelBackoff == null ? List.of() : List.copyOf(cancelBackoff);
        if (cancelBatchSize < 1 || cancelGap == null || cancelGap.isNegative() || cancelMaxRetries < 0 || cancelBackoff.size() < cancelMaxRetries
                || cancelBackoff.stream().anyMatch(d -> d.isNegative() || d.isZero()) || cancelStale == null || cancelStale.isNegative() || cancelStale.isZero()) {
            throw new IllegalArgumentException("roboyam.payment.* 설정이 범위를 벗어났다(재시도 간격은 재시도 횟수만큼 있어야 한다)");
        }
    }

    /** 시도 한도(즉시 시도 1 + 재시도) */
    public int cancelMaxAttempts() {
        return 1 + cancelMaxRetries;
    }

    /** {@code attempts} 번째 시도가 실패한 뒤 다음 시도까지 */
    public Duration backoffAfter(int attempts) {
        int index = Math.max(0, Math.min(attempts - 1, cancelBackoff.size() - 1));
        return cancelBackoff.get(index);
    }
}
