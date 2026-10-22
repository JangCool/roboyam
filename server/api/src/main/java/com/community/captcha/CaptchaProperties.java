package com.community.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.captcha.* — 자동입력 방지 설정.
 *
 * @param turnstileSecret Cloudflare Turnstile 비밀키(환경 변수 {@code TURNSTILE_SECRET_KEY}). 비어 있으면 검증하지 않는다. 로그·응답에 싣지 않는다
 */
@ConfigurationProperties(prefix = "roboyam.captcha")
public record CaptchaProperties(String turnstileSecret) {

    @Override
    public String toString() {
        return "CaptchaProperties[turnstileSecret=(감춤)]";
    }
}
