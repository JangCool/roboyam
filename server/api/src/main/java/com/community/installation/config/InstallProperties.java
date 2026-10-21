package com.community.installation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.install.* — 설치 마법사 설정 (docs/features/install-contract.md).
 *
 * <p>{@code token} 은 환경 변수 {@code ROBOYAM_INSTALL_TOKEN} 이다. 값이 있으면 그것이 설치 토큰이고(자동화·컨테이너·앱 서버 여러 대용 — 로그에 찍지 않는다),
 * 비어 있으면 서버가 뜰 때 무작위 토큰을 만들어 <b>로그에 한 번</b> 출력한다. local 만 개발용 기본 토큰을 내장한다({@code roboyam-shared-local.yml}) —
 * dev · prod 에는 기본값이 없다. 값이 로그·오류에 새지 않도록 {@link #toString()} 은 값을 감춘다.
 *
 * @param token           설치 토큰. 비어 있으면 무작위로 만든다
 * @param maxFailures     같은 IP 가 연속으로 틀릴 수 있는 횟수. 이 횟수째 틀리면 잠근다
 * @param lockSeconds     잠그는 시간(초)
 * @param globalPerMinute 서버 전체에서 한 분 동안 받는 토큰 확인 시도 상한(IP 를 바꿔 가며 두드리는 것을 막는다)
 */
@ConfigurationProperties(prefix = "roboyam.install")
public record InstallProperties(String token, int maxFailures, long lockSeconds, int globalPerMinute) {

    /** 환경 변수로 정한 토큰이 있는가 */
    public boolean hasConfiguredToken() {
        return token != null && !token.isBlank();
    }

    @Override
    public String toString() {
        return "InstallProperties[token=" + (hasConfiguredToken() ? "(감춤)" : "(없음)") + ", maxFailures=" + maxFailures
                + ", lockSeconds=" + lockSeconds + ", globalPerMinute=" + globalPerMinute + "]";
    }
}
