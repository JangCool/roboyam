package com.community.mfa.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 관리자 2차 인증의 구성 — 시계를 빈으로 주입한다(TOTP 시간 칸·2차 대기 5분·유예·신뢰 기기 기간·실패 잠금을 시험이 시각을 정해 확인할 수 있게).
 * 운영에서는 UTC 시스템 시계다. 이 시계는 2차 인증 코드만 쓴다(다른 업무는 지금처럼 {@code Instant.now()}).
 */
@Configuration
public class MfaConfig {

    @Bean
    public Clock mfaClock() {
        return Clock.systemUTC();
    }
}
