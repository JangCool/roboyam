package com.community.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.auth.* — 로그인·인증의 수치(docs/features/login-contract.md). 모두 설정값이다.
 *
 * @param loginMaxFailures     같은 이메일로 연속으로 틀릴 수 있는 횟수(이 횟수째 틀리면 잠근다)
 * @param loginLockSeconds     잠그는 시간(초)
 * @param verifyTokenHours     이메일 인증 링크의 유효 시간
 * @param resendCooldownSeconds 인증 메일 재발송 간격(초) — 가장 최근에 보낸 링크 기준
 * @param sessionTimeout       로그인 세션의 유지 기간 — 마지막 활동 뒤 이 기간(활동하면 연장)
 */
@ConfigurationProperties(prefix = "roboyam.auth")
public record AuthProperties(int loginMaxFailures, int loginLockSeconds, int verifyTokenHours, int resendCooldownSeconds,
                             Duration sessionTimeout) {
}
