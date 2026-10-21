package com.community.batch.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.batch.* — 배치 스케줄러의 수치(docs/features/roles-contract.md 「배치 인프라 결과」 가정). 모두 설정값이다.
 *
 * @param schedulerEnabled   이 서버가 예약 실행을 하는가. 끄면 「지금 실행」만 된다(서버 여러 대 중 일부에서만 예약을 돌리고 싶을 때)
 * @param zone               cron 식을 해석하는 시간대(IANA 이름)
 * @param refreshSeconds     다른 서버가 바꾼 사용 여부·cron 을 따라오는 주기(초)
 * @param minIntervalSeconds cron 으로 정할 수 있는 실행 간격의 하한(초)
 * @param logRetentionDays   실행 로그를 보관하는 일수(「배치 로그 정리」 배치가 이보다 오래된 로그를 지운다)
 * @param poolSize           예약 실행에 쓰는 스레드 수(배치 여러 개가 같은 시각에 도는 것을 견딘다)
 */
@ConfigurationProperties(prefix = "roboyam.batch")
public record BatchProperties(boolean schedulerEnabled, String zone, int refreshSeconds, int minIntervalSeconds, int logRetentionDays, int poolSize) {

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
