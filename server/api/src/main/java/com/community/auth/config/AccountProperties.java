package com.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.account.* — 회원 계정 흐름의 수치(docs/features/member-account-contract.md). 재발송 간격·로그인 잠금은 {@link AuthProperties} 의 값을 함께 쓴다.
 *
 * @param resetTokenMinutes     비밀번호 재설정 링크의 유효 시간(분)
 * @param emailChangeTokenHours 이메일 변경 링크의 유효 시간
 */
@ConfigurationProperties(prefix = "roboyam.account")
public record AccountProperties(@DefaultValue("60") int resetTokenMinutes, @DefaultValue("24") int emailChangeTokenHours) {
}
