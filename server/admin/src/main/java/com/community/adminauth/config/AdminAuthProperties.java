package com.community.adminauth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.admin-auth.* — 관리자 앱 로그인·세션·초대의 수치(docs/features/roles-contract.md 가정). 모두 설정값이다.
 *
 * @param loginMaxFailures    같은 이메일로 연속으로 틀릴 수 있는 횟수(이 횟수째 틀리면 잠근다)
 * @param loginLockSeconds    잠그는 시간(초)
 * @param sessionIdleTimeout  세션 비활동 기한 — 마지막 활동 뒤 이 기간
 * @param sessionMaxAge       세션 최대 길이 — 활동해도 로그인한 지 이 기간이 지나면 끝난다
 * @param invitationValidHours 운영자 초대 링크의 유효 시간
 */
@ConfigurationProperties(prefix = "roboyam.admin-auth")
public record AdminAuthProperties(int loginMaxFailures, int loginLockSeconds, Duration sessionIdleTimeout, Duration sessionMaxAge,
                                  int invitationValidHours) {
}
