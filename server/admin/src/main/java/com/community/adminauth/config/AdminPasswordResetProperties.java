package com.community.adminauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.admin-auth.password-reset.* — 운영자 비밀번호 재설정(승인번호)의 수치. 승인번호 자릿수(6)는 응답 계약이라 코드 상수다.
 * 값은 가정이다(docs/features/roles-contract.md 「5단계 구현 결과」).
 *
 * @param codeValidMinutes     승인번호 유효 시간(분) — hamonica3 와 같다
 * @param maxFailures          한 번호로 틀릴 수 있는 횟수(이 횟수째 틀리면 소진 — 다시 받아야 한다)
 * @param cooldownSeconds      같은 이메일로 승인번호를 다시 받을 수 있기까지의 초
 * @param windowMinutes        시간당 한도를 세는 창(분)
 * @param emailLimit           창 안에서 이메일(해시 키) 하나가 받을 수 있는 승인번호 요청 수
 * @param ipLimit              창 안에서 IP 하나가 보낼 수 있는 승인번호 요청 수
 */
@ConfigurationProperties(prefix = "roboyam.admin-auth.password-reset")
public record AdminPasswordResetProperties(@DefaultValue("10") int codeValidMinutes, @DefaultValue("5") int maxFailures,
                                           @DefaultValue("60") int cooldownSeconds, @DefaultValue("60") int windowMinutes,
                                           @DefaultValue("5") int emailLimit, @DefaultValue("20") int ipLimit) {

    /** 승인번호 자릿수 — 응답의 {@code codeLength} */
    public static final int CODE_LENGTH = 6;
}
