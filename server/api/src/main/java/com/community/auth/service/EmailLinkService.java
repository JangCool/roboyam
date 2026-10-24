package com.community.auth.service;

import com.community.auth.dto.response.EmailVerifyResponse;
import com.community.auth.entity.AuthTokenEntity;
import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.exception.AuthTokenExpiredException;
import com.community.auth.exception.AuthTokenInvalidException;
import com.community.auth.repository.master.AuthTokenRepository;
import com.community.auth.repository.master.AuthTokenRepositoryQDSL;
import com.community.user.entity.UserEntity;
import com.community.user.exception.UserEmailTakenException;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.crypto.Digests;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일 링크 확인 {@code POST /api/auth/email/verify} — 로그인 없이 된다(다른 브라우저에서 눌러도 된다). <b>이메일 인증 링크와 이메일 변경 링크가 같은 화면 주소</b>
 * ({@code /verify-email?token=})로 오므로 토큰의 용도로 가른다. 비밀번호 재설정 링크는 여기서 쓸 수 없다.
 *
 * <ul>
 *   <li><b>검사 순서</b>: 토큰 없음·모르는 토큰·다른 용도·이미 씀·탈퇴한 회원 {@code 400 TOKEN_INVALID} → 만료 {@code 410 TOKEN_EXPIRED} →
 *       (변경 링크) 새 주소를 그 사이 다른 회원이 씀 {@code 409 EMAIL_TAKEN}(링크는 쓰지 않은 채 둔다).</li>
 *   <li><b>인증 링크</b>: 이메일 인증 완료({@code email_verified_at}) + 그 회원의 인증 링크를 모두 사용 처리 → {@code { verified: true, changed: false }}.</li>
 *   <li><b>변경 링크</b>: 로그인 이메일을 새 주소로 바꾸고 인증된 상태로 둔다 + 변경 링크·(옛 주소로 보낸) 인증 링크를 모두 사용 처리 → {@code changed: true}.
 *       다른 기기의 세션은 그대로다(community {@code api-account.md}). 정지 회원도 된다(개인 설정).</li>
 * </ul>
 * 회원 행을 먼저 잠그고 토큰을 읽는다 — 같은 회원의 링크 발급({@link VerificationTokenService}·이메일 변경 요청)과 같은 잠금 순서라 동시 요청이 줄 선다.
 * 토큰·이메일을 로그에 남기지 않는다.
 */
@Service
public class EmailLinkService {

    private static final Logger log = LoggerFactory.getLogger(EmailLinkService.class);

    /** 토큰 원문의 최대 길이 — 발급 토큰은 43자다. 이보다 길면 해시하지 않고 거절한다 */
    static final int TOKEN_MAX_LENGTH = 128;

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final AuthTokenRepositoryQDSL tokenQueries;

    public EmailLinkService(UserRepository users, AuthTokenRepository tokens, AuthTokenRepositoryQDSL tokenQueries) {
        this.users = users;
        this.tokens = tokens;
        this.tokenQueries = tokenQueries;
    }

    @Transactional
    public EmailVerifyResponse verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > TOKEN_MAX_LENGTH) {
            throw new AuthTokenInvalidException();
        }
        String hash = Digests.sha256Hex(rawToken);
        long userId = tokenQueries.findUserIdByHash(hash).orElseThrow(AuthTokenInvalidException::new);
        UserEntity user = users.findWithLockById(userId).filter(u -> !u.isGone()).orElseThrow(AuthTokenInvalidException::new);
        AuthTokenEntity token = tokens.findByTokenHash(hash).orElseThrow(AuthTokenInvalidException::new);
        Instant now = Instant.now();
        if (token.getPurpose() == AuthTokenPurpose.RESET_PASSWORD || token.isConsumed()) {
            throw new AuthTokenInvalidException();
        }
        if (token.isExpired(now)) {
            throw new AuthTokenExpiredException();
        }

        if (token.getPurpose() == AuthTokenPurpose.VERIFY_EMAIL) {
            if (user.getEmail() == null) {
                throw new AuthTokenInvalidException();
            }
            user.markEmailVerified(now);
            token.consume(now);
            tokenQueries.consumeAll(userId, AuthTokenPurpose.VERIFY_EMAIL, now);
            log.info("이메일 인증 완료 userId={}", userId);
            return new EmailVerifyResponse(true, false);
        }

        String newEmail = token.getNewEmail();
        if (newEmail == null) {
            throw new AuthTokenInvalidException();
        }
        if (users.findByEmail(newEmail).filter(other -> !other.getId().equals(userId)).isPresent()) {
            throw new UserEmailTakenException();
        }
        user.changeEmail(newEmail, now);
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            throw new UserEmailTakenException(); // 동시에 다른 회원이 그 주소를 썼다(유일 제약 ux_users_email)
        }
        token.consume(now);
        tokenQueries.consumeAll(userId, AuthTokenPurpose.CHANGE_EMAIL, now);
        tokenQueries.consumeAll(userId, AuthTokenPurpose.VERIFY_EMAIL, now); // 옛 주소로 보낸 인증 링크가 새 주소를 인증하지 못하게
        log.info("이메일 변경 완료 userId={}", userId);
        return new EmailVerifyResponse(true, true);
    }
}
