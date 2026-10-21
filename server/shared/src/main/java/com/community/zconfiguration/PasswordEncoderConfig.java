package com.community.zconfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 사용자 비밀번호 해시 (bcrypt).
 *
 * <p>되돌릴 수 없어야 하므로 양방향 암호화(접속 정보 등)와 같은 것으로 다루지 않는다.
 *
 * <p>이 빈 하나를 서버 전체가 공유한다. 가입이 계정을 만들 때와 로그인이 비밀번호를 대조할 때 방식이 갈리면
 * 만든 계정으로 로그인이 안 되므로 {@code SecurityConfig} 에 또 만들지 않는다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
