package com.community.captcha;

import com.community.notification.net.SafeHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 자동입력 방지 구현 고르기 — 비밀키가 있으면 Turnstile, 없으면(local·설치 직후) 「검증 안 함」이고 기동 로그에 경고를 남긴다.
 * 검증하지 않는 동안 비회원 글은 작성 간격·하루 한도·링크 수·금지어로만 막힌다(docs/features/board-public-contract.md 추가 결정 1).
 */
@Configuration
public class CaptchaConfig {

    private static final Logger log = LoggerFactory.getLogger(CaptchaConfig.class);

    @Bean
    public CaptchaVerifier captchaVerifier(CaptchaProperties props, SafeHttpClient http) {
        String secret = props.turnstileSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("자동입력 방지 비밀키(TURNSTILE_SECRET_KEY)가 없어 비회원 글·댓글의 캡차를 검증하지 않는다 — 운영 환경이면 설정한다");
            return (token, remoteIp) -> true;
        }
        log.info("자동입력 방지: Cloudflare Turnstile 로 검증한다");
        return new TurnstileCaptchaVerifier(http, secret.strip());
    }
}
