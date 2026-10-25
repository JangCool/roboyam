package com.community.auth.service;

import com.community.auth.config.AccountProperties;
import com.community.auth.config.AuthProperties;
import com.community.auth.dto.PasswordForgotTarget;
import com.community.auth.dto.PasswordResetTarget;
import com.community.auth.entity.AuthTokenEntity;
import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.exception.AuthTokenExpiredException;
import com.community.auth.exception.AuthTokenInvalidException;
import com.community.auth.repository.master.AuthTokenRepository;
import com.community.auth.repository.master.AuthTokenRepositoryQDSL;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserIdentityEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserIdentityRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 링크의 DB 일 — 메서드마다 짧은 트랜잭션이다(비밀번호 해시 비교·만들기는 이 밖에서 한다). 흐름은 {@link PasswordResetService}.
 *
 * <ul>
 *   <li>링크 토큰은 무작위 256비트, <b>해시만 저장</b>, 유효 {@code roboyam.account.reset-token-minutes}(60분). 새 링크를 만들면 이전 링크는 무효다.</li>
 *   <li>같은 회원은 60초에 한 번만 새 링크를 만든다(가장 최근 발급 시각 — 서버가 여러 대여도 같다). 넘치면 조용히 보내지 않는다.</li>
 *   <li>회원 행을 먼저 잠그고 토큰을 읽는다(인증·이메일 변경 링크와 같은 잠금 순서).</li>
 * </ul>
 */
@Service
public class PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final UserIdentityRepository identities;
    private final AuthTokenRepository tokens;
    private final AuthTokenRepositoryQDSL tokenQueries;
    private final SnowflakeIdGenerator ids;
    private final AuthProperties authProps;
    private final AccountProperties props;
    private final LoginAttemptService attempts;
    private final MemberSessionTerminator sessions;

    public PasswordResetTokenService(UserRepository users, ProfileRepository profiles, UserIdentityRepository identities, AuthTokenRepository tokens,
                                     AuthTokenRepositoryQDSL tokenQueries, SnowflakeIdGenerator ids, AuthProperties authProps, AccountProperties props,
                                     LoginAttemptService attempts, MemberSessionTerminator sessions) {
        this.users = users;
        this.profiles = profiles;
        this.identities = identities;
        this.tokens = tokens;
        this.tokenQueries = tokenQueries;
        this.ids = ids;
        this.authProps = authProps;
        this.props = props;
        this.attempts = attempts;
        this.sessions = sessions;
    }

    /**
     * 비밀번호 찾기 작업(요청 스레드 밖)이 보낼 메일을 정한다 — 비밀번호가 있는 회원은 새 재설정 링크, 소셜로만 가입한 회원은 가입한 소셜 안내,
     * 없는 이메일·탈퇴·60초 안 반복·로그인 수단이 없음은 보내지 않는다.
     *
     * @param email 정규화한 이메일
     */
    @Transactional
    public PasswordForgotTarget prepareForgot(String email) {
        Optional<UserEntity> found = users.findByEmail(email).filter(u -> !u.isGone());
        if (found.isEmpty()) {
            return PasswordForgotTarget.none();
        }
        long userId = found.get().getId();
        UserEntity user = users.findWithLockById(userId).filter(u -> !u.isGone()).orElse(null);
        if (user == null || user.getEmail() == null) {
            return PasswordForgotTarget.none();
        }
        String nickname = profiles.findById(userId).map(ProfileEntity::getNickname).orElse(null);

        if (!user.hasPassword()) {
            return identities.findByUserIdOrderByCreatedAtAsc(userId).stream().findFirst().map(UserIdentityEntity::getProvider)
                    .map(provider -> new PasswordForgotTarget(PasswordForgotTarget.Kind.SOCIAL, userId, user.getEmail(), nickname, null, provider))
                    .orElseGet(PasswordForgotTarget::none);
        }

        Instant now = Instant.now();
        long cooldownMillis = Duration.ofSeconds(authProps.resendCooldownSeconds()).toMillis();
        boolean cooling = tokenQueries.findLatestCreatedAt(userId, AuthTokenPurpose.RESET_PASSWORD)
                .filter(latest -> Duration.between(latest, now).toMillis() < cooldownMillis).isPresent();
        if (cooling) {
            log.info("비밀번호 재설정 링크 — 60초 안의 반복이라 보내지 않았다 userId={}", userId);
            return PasswordForgotTarget.none();
        }
        tokenQueries.consumeAll(userId, AuthTokenPurpose.RESET_PASSWORD, now);
        String raw = Digests.newToken();
        tokens.persist(AuthTokenEntity.create(ids.nextId(), userId, AuthTokenPurpose.RESET_PASSWORD, Digests.sha256Hex(raw),
                now.plus(Duration.ofMinutes(props.resetTokenMinutes())), now));
        return new PasswordForgotTarget(PasswordForgotTarget.Kind.RESET, userId, user.getEmail(), nickname, raw, null);
    }

    /**
     * 재설정 링크를 아직 쓸 수 있는지 보고, 그 회원과 지금 비밀번호 해시를 돌려준다.
     *
     * @throws AuthTokenInvalidException 없음·이미 씀·다른 용도·탈퇴·비밀번호 없는 회원(400)
     * @throws AuthTokenExpiredException 만료(410)
     */
    @Transactional(readOnly = true)
    public PasswordResetTarget check(String rawToken) {
        String hash = hashOf(rawToken);
        AuthTokenEntity token = tokens.findByTokenHash(hash).orElseThrow(AuthTokenInvalidException::new);
        assertUsable(token, Instant.now());
        UserEntity user = users.findById(token.getUserId()).filter(u -> !u.isGone() && u.hasPassword()).orElseThrow(AuthTokenInvalidException::new);
        return new PasswordResetTarget(user.getId(), user.getPasswordHash());
    }

    /**
     * 비밀번호를 바꾼다 — 링크를 다시 확인(회원 행을 잠근 뒤) → 비밀번호 교체 → 재설정 링크 모두 사용 처리 → 로그인 실패 횟수·잠금 초기화 → <b>그 회원의 모든 세션 종료</b>.
     *
     * @param newPasswordHash      새 비밀번호의 bcrypt 해시
     * @param expectedPasswordHash {@link #check} 때 읽은 해시 — 그 사이 비밀번호가 바뀌었으면 이 링크로는 바꾸지 않는다(400 {@code TOKEN_INVALID})
     * @return 끝낸 세션 수
     */
    @Transactional
    public int applyReset(String rawToken, String newPasswordHash, String expectedPasswordHash) {
        String hash = hashOf(rawToken);
        long userId = tokenQueries.findUserIdByHash(hash).orElseThrow(AuthTokenInvalidException::new);
        UserEntity user = users.findWithLockById(userId).filter(u -> !u.isGone() && u.hasPassword()).orElseThrow(AuthTokenInvalidException::new);
        AuthTokenEntity token = tokens.findByTokenHash(hash).orElseThrow(AuthTokenInvalidException::new);
        Instant now = Instant.now();
        assertUsable(token, now);
        if (!Objects.equals(user.getPasswordHash(), expectedPasswordHash)) {
            throw new AuthTokenInvalidException();
        }

        user.changePassword(newPasswordHash, now);
        token.consume(now);
        tokenQueries.consumeAll(userId, AuthTokenPurpose.RESET_PASSWORD, now);
        if (user.getEmail() != null) {
            attempts.resetFailures(user.getEmail());
        }
        int ended = sessions.endAll(userId);
        log.info("비밀번호를 재설정했다 userId={} 끝낸 세션={}개", userId, ended);
        return ended;
    }

    private static String hashOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > EmailLinkService.TOKEN_MAX_LENGTH) {
            throw new AuthTokenInvalidException();
        }
        return Digests.sha256Hex(rawToken);
    }

    private static void assertUsable(AuthTokenEntity token, Instant now) {
        if (token.getPurpose() != AuthTokenPurpose.RESET_PASSWORD || token.isConsumed()) {
            throw new AuthTokenInvalidException();
        }
        if (token.isExpired(now)) {
            throw new AuthTokenExpiredException();
        }
    }
}
