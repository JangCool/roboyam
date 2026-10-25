package com.community.identity.config;

import com.community.identity.dto.IdentityProviderKind;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.identity.* — 본인인증(docs/features/commerce-foundation-contract.md 「0-4」, 세션 정리 결정 9).
 *
 * @param provider        본인인증 구현 — {@code portone}(포트원 통합 본인인증) · {@code fake}(local·시험 전용, <b>dev·prod 프로필에서는 기동을 멈춘다</b> — {@link IdentityConfig}).
 *                        배포 설정이라 화면에서 바꾸지 않는다. 환경 변수 {@code ROBOYAM_IDENTITY_PROVIDER}. 공통 기본값은 portone, local 은 fake
 * @param verificationTtl 서버가 발급한 인증 번호의 유효 시간(30분) — 지나면 확정할 수 없다(410)
 */
@ConfigurationProperties(prefix = "roboyam.identity")
public record IdentityProperties(@DefaultValue("portone") IdentityProviderKind provider, @DefaultValue("30m") Duration verificationTtl) {
}
