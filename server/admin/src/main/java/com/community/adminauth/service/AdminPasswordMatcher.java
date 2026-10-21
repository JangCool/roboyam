package com.community.adminauth.service;

import com.community.user.service.UserPolicy;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 해시 비교 — 예외 없이 맞다/아니다로만 답한다. bcrypt 가 다룰 수 없는 72바이트 초과 입력이나 읽을 수 없는 해시는 불일치이고,
 * 시간이 다르지 않게 더미 비교를 한 번 한다(로그인 {@code AdminLoginService} 와 같은 규칙).
 */
@Component
public class AdminPasswordMatcher {

    private static final String TIMING_DUMMY_PASSWORD = "roboyam-timing-dummy-password-1";

    private final PasswordEncoder encoder;

    public AdminPasswordMatcher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public boolean matches(String raw, String hash) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > UserPolicy.PASSWORD_MAX_BYTES) {
            encoder.matches(TIMING_DUMMY_PASSWORD, hash);
            return false;
        }
        try {
            return encoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
