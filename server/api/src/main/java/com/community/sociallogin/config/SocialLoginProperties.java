package com.community.sociallogin.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.social.* — 소셜 로그인(OAuth 2.0 인가 코드 흐름)의 수치. docs/features/login-contract.md 「소셜 로그인」.
 *
 * @param stateTtl          시작한 흐름(state·PKCE·nonce)의 유효 시간 — 이 안에 공급자에서 돌아와야 한다
 * @param pendingTtl        처음 보는 소셜 계정의 가입 완료 대기(서버 세션의 임시 정보) 유효 시간
 * @param rateWindow        IP 기준 요청 한도를 세는 창
 * @param startPerWindow    한 IP 가 창 안에 시작(로그인·연결)할 수 있는 횟수
 * @param completePerWindow 한 IP 가 창 안에 가입 완료를 요청할 수 있는 횟수
 * @param connectTimeout    공급자 접속 제한 시간
 * @param readTimeout       공급자 응답 제한 시간(요청 하나 전체)
 * @param testEndpoints     <b>local 프로필 전용</b> 가짜 공급자 주소 — {@link SocialEndpoints} 가 local 이 아니거나 루프백 주소가 아니면 무시한다
 */
@ConfigurationProperties(prefix = "roboyam.social")
public record SocialLoginProperties(Duration stateTtl, Duration pendingTtl, Duration rateWindow, int startPerWindow, int completePerWindow,
                                    Duration connectTimeout, Duration readTimeout, TestEndpoints testEndpoints) {

    public SocialLoginProperties {
        stateTtl = stateTtl == null ? Duration.ofMinutes(10) : stateTtl;
        pendingTtl = pendingTtl == null ? Duration.ofMinutes(15) : pendingTtl;
        rateWindow = rateWindow == null ? Duration.ofMinutes(10) : rateWindow;
        startPerWindow = startPerWindow <= 0 ? 30 : startPerWindow;
        completePerWindow = completePerWindow <= 0 ? 10 : completePerWindow;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        testEndpoints = testEndpoints == null ? new TestEndpoints(false, null) : testEndpoints;
    }

    /**
     * 시험용 가짜 공급자 — 켜면 세 공급자의 주소가 {@code baseUrl + "/kakao/authorize"} 같은 모양으로 바뀐다. 기본 꺼짐이고, local 프로필(dev·prod 가 아님) +
     * 루프백 주소일 때만 쓰인다.
     *
     * @param enabled 켤 것인가
     * @param baseUrl {@code http://127.0.0.1:포트} 처럼 루프백 주소(끝 {@code /} 없음)
     */
    public record TestEndpoints(boolean enabled, String baseUrl) {
    }
}
