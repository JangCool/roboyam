package com.community.auth.service;

import com.community.user.service.UserPolicy;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 비교 — 예외 없이 참/거짓만 낸다. 72바이트를 넘는 입력(bcrypt 가 다룰 수 없다)·읽을 수 없는 해시·해시 없음은 불일치다
 * ({@link LoginService} 의 규칙과 같다). 비밀번호 변경·이메일 변경의 현재 비밀번호 확인, 재설정의 「지금과 같은가」가 쓴다.
 */
public final class PasswordMatching {

    private PasswordMatching() {
    }

    public static boolean matches(PasswordEncoder encoder, String raw, String hash) {
        if (raw == null || raw.isEmpty() || hash == null || hash.isEmpty()) {
            return false;
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > UserPolicy.PASSWORD_MAX_BYTES) {
            return false;
        }
        try {
            return encoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
