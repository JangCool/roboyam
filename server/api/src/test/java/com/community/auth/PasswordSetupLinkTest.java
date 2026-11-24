package com.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.community.auth.exception.AuthTokenInvalidException;
import com.community.auth.service.EmailLinkService;
import com.community.auth.service.PasswordResetService;
import com.community.commerce.CommerceApiTestBase;
import com.community.zconfiguration.crypto.Digests;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 정하기 링크(용도 {@code SETUP_PASSWORD} — 관리자가 만든 운영자 회원 계정, operator-member-account-contract 「새 계정 만들기」)가 비밀번호 재설정 화면·API 로 쓰이고,
 * 정하면 이메일 인증도 끝나는지 실제 PostgreSQL 에서 확인한다. 재설정 링크는 그대로 인증을 바꾸지 않는다.
 */
class PasswordSetupLinkTest extends CommerceApiTestBase {

    @Autowired
    private PasswordResetService reset;
    @Autowired
    private EmailLinkService emailLinks;
    @Autowired
    private PasswordEncoder encoder;

    /** 인증 전 회원(아무도 모르는 비밀번호)과 그 회원의 링크 하나 — 원문을 돌려준다 */
    private String unverifiedWithLink(long userId, String purpose) {
        // SQL 로 남김: 인증 칸을 비우고 비밀번호 해시를 바로 두는 도메인 메서드가 없다 · 토큰은 메일 대기열이 보낼 때 만드는 것과 같은 모양으로 넣는다
        jdbc.update("UPDATE users SET email_verified_at = NULL, password_hash = ? WHERE id = ?", encoder.encode(Digests.newToken()), userId);
        String raw = Digests.newToken();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO auth_tokens (id, user_id, purpose, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)", ids.nextId(), userId, purpose,
                Digests.sha256Hex(raw), ts(now.plus(72, ChronoUnit.HOURS)), ts(now));
        return raw;
    }

    @Test
    void 정하기_링크로_비밀번호를_정하면_이메일_인증도_끝나고_링크는_다시_못_쓴다() {
        long userId = newMember();
        String raw = unverifiedWithLink(userId, "SETUP_PASSWORD");

        reset.check(raw);
        reset.reset(raw, "Setup1pass");

        Map<String, Object> user = jdbc.queryForMap("SELECT email_verified_at, password_hash FROM users WHERE id = ?", userId);
        assertThat(user.get("email_verified_at")).isNotNull();
        assertThat(encoder.matches("Setup1pass", (String) user.get("password_hash"))).isTrue();
        assertThat(catchThrowable(() -> reset.check(raw))).isInstanceOf(AuthTokenInvalidException.class);
    }

    @Test
    void 재설정_링크는_인증을_바꾸지_않고_정하기_링크는_인증_링크로_쓸_수_없다() {
        long userId = newMember();
        String resetRaw = unverifiedWithLink(userId, "RESET_PASSWORD");
        reset.reset(resetRaw, "Reset1pass");
        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NULL FROM users WHERE id = ?", Boolean.class, userId)).isTrue();

        long other = newMember();
        String setupRaw = unverifiedWithLink(other, "SETUP_PASSWORD");
        assertThat(catchThrowable(() -> emailLinks.verify(setupRaw))).isInstanceOf(AuthTokenInvalidException.class);
        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NULL FROM users WHERE id = ?", Boolean.class, other)).isTrue();
    }
}
