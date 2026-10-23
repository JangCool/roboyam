package com.community.batch.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.batch.* — 배치를 도는 인스턴스의 역할과 수치(docs/features/roles-contract.md 「배치 인프라 결과」, docs/domain/commerce.md 15-1·15-4). 모두 배포 설정이다.
 *
 * @param enabled              이 서버가 배치를 도는가 — <b>하나로</b> 예약 실행·실행 요청 집기·짧은 주기 워커가 함께 켜지고 꺼진다(결정 6). 끈 서버도 화면 API(목록·로그·요청 넣기)는
 *                             그대로다 — 요청은 배치를 켠 다른 서버가 집는다. 같은 관리자 이미지를 「배치 전용」으로 하나 더 띄우고 나머지는 끄면 사실상 배치 서버다
 * @param zone                 cron 식을 해석하는 시간대(IANA 이름)
 * @param refreshSeconds       다른 서버가 바꾼 사용 여부·cron 을 따라오고, 죽은 실행·남은 요청을 정리하는 주기(초)
 * @param minIntervalSeconds   cron 으로 정할 수 있는 실행 간격의 하한(초)
 * @param poolSize             예약 실행 스레드 수, 그리고 요청 실행을 동시에 몇 개까지 돌리는가(배치 여러 개가 같은 시각에 도는 것을 견딘다)
 * @param requestPollMillis    실행 요청을 집는 주기(밀리초) — 「지금 실행」이 시작되기까지 최대 이만큼 늦는다
 * @param requestStaleSeconds  집은 지 이만큼 지나도 RUNNING 인 요청을 정리 대상으로 본다(초) — 집은 서버가 죽은 경우
 * @param heartbeatIdleSeconds 일이 없을 때 워커 기록({@code worker_heartbeats})을 남기는 간격(초)
 *
 * <p>실행 로그 보관 일수는 여기 없다 — 로그 정리는 DB 배치({@code batch-log-cleanup})이고 보관 일수는 DB 의 {@code batch_params} 에 있다.
 * {@code zone} 은 Java 배치의 cron 해석 시간대이고, DB 배치의 cron 은 pg_cron 의 {@code cron.timezone} 으로 해석된다.
 */
@ConfigurationProperties(prefix = "roboyam.batch")
public record BatchProperties(boolean enabled, String zone, int refreshSeconds, int minIntervalSeconds, int poolSize, long requestPollMillis, int requestStaleSeconds,
                              int heartbeatIdleSeconds) {

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    public Duration requestStale() {
        return Duration.ofSeconds(Math.max(30, requestStaleSeconds));
    }
}
