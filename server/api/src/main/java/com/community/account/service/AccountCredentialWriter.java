package com.community.account.service;

import com.community.account.dto.IssuedEmailChange;
import com.community.account.exception.AccountCurrentPasswordWrongException;
import com.community.account.exception.AccountEmailChangeNotAllowedException;
import com.community.account.exception.AccountPasswordNotSetException;
import com.community.auth.config.AccountProperties;
import com.community.auth.config.AuthProperties;
import com.community.auth.entity.AuthTokenEntity;
import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.exception.AuthResendCooldownException;
import com.community.auth.repository.master.AuthTokenRepository;
import com.community.auth.repository.master.AuthTokenRepositoryQDSL;
import com.community.auth.service.LoginAttemptService;
import com.community.auth.service.MemberSessionTerminator;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 계정의 비밀번호 변경·이메일 변경 요청의 DB 일 — 메서드마다 짧은 트랜잭션이다(현재 비밀번호 확인·새 해시 만들기는 이 밖에서 끝낸 뒤 부른다).
 * 회원 행을 잠가 같은 회원의 동시 요청을 줄 세운다. 이메일·토큰을 로그에 남기지 않는다.
 */
@Service
public class AccountCredentialWriter {

    private static final Logger log = LoggerFactory.getLogger(AccountCredentialWriter.class);

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AuthTokenRepository tokens;
    private final AuthTokenRepositoryQDSL tokenQueries;
    private final SnowflakeIdGenerator ids;
    private final LoginAttemptService attempts;
    private final MemberSessionTerminator sessions;
    private final AuthProperties authProps;
    private final AccountProperties props;

    public AccountCredentialWriter(UserRepository users, ProfileRepository profiles, AuthTokenRepository tokens, AuthTokenRepositoryQDSL tokenQueries,
                                   SnowflakeIdGenerator ids, LoginAttemptService attempts, MemberSessionTerminator sessions, AuthProperties authProps,
                                   AccountProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.tokens = tokens;
        this.tokenQueries = tokenQueries;
        this.ids = ids;
        this.attempts = attempts;
        this.sessions = sessions;
        this.authProps = authProps;
        this.props = props;
    }

    /**
     * 비밀번호를 바꾼다 → 재설정 링크 무효 → 로그인 실패 횟수·잠금 초기화 → <b>{@code keepSessionId} 를 뺀 그 회원의 다른 세션 종료</b>.
     *
     * @param expectedPasswordHash 현재 비밀번호를 확인할 때 읽은 해시 — 그 사이 비밀번호가 바뀌었으면 확인이 낡았으므로 거절한다(401, 횟수에 세지 않는다)
     * @return 끝낸 세션 수
     */
    @Transactional
    public int changePassword(long userId, String expectedPasswordHash, String newPasswordHash, String keepSessionId) {
        UserEntity user = lockMember(userId);
        if (!user.hasPassword()) {
            throw new AccountPasswordNotSetException();
        }
        if (!Objects.equals(user.getPasswordHash(), expectedPasswordHash)) {
            throw new AccountCurrentPasswordWrongException();
        }
        Instant now = Instant.now();
        user.changePassword(newPasswordHash, now);
        tokenQueries.consumeAll(userId, AuthTokenPurpose.RESET_PASSWORD, now);
        attempts.resetFailures(CurrentPasswordVerifier.attemptKey(user));
        int ended = sessions.endOthers(userId, keepSessionId);
        log.info("비밀번호를 바꿨다 userId={} 끝낸 다른 세션={}개", userId, ended);
        return ended;
    }

    /**
     * 이메일 변경 링크를 만든다 — 60초 제한 → 이전 변경 링크 무효 → 새 링크(새 주소, 유효 {@code roboyam.account.email-change-token-hours}) → 로그인 실패 횟수 초기화
     * (현재 비밀번호를 맞혔다). 링크를 누르기 전까지 지금 이메일은 그대로다.
     *
     * @param newEmail 정규화한 새 주소(겹침은 부르는 쪽이 이미 봤다 — 링크를 누를 때 다시 본다)
     * @throws AuthResendCooldownException 60초가 안 지났을 때(429)
     */
    @Transactional
    public IssuedEmailChange issueEmailChange(long userId, String newEmail) {
        UserEntity user = lockMember(userId);
        if (!user.hasPassword() || user.getEmail() == null) {
            throw new AccountEmailChangeNotAllowedException();
        }
        Instant now = Instant.now();
        long cooldownMillis = Duration.ofSeconds(authProps.resendCooldownSeconds()).toMillis();
        tokenQueries.findLatestCreatedAt(userId, AuthTokenPurpose.CHANGE_EMAIL).ifPresent(latest -> {
            long remainingMillis = Math.min(cooldownMillis, cooldownMillis - Duration.between(latest, now).toMillis());
            if (remainingMillis > 0) {
                throw new AuthResendCooldownException((remainingMillis + 999) / 1000);
            }
        });

        tokenQueries.consumeAll(userId, AuthTokenPurpose.CHANGE_EMAIL, now);
        String raw = Digests.newToken();
        tokens.persist(AuthTokenEntity.createEmailChange(ids.nextId(), userId, newEmail, Digests.sha256Hex(raw),
                now.plus(Duration.ofHours(props.emailChangeTokenHours())), now));
        attempts.resetFailures(CurrentPasswordVerifier.attemptKey(user));

        String nickname = profiles.findById(userId).map(ProfileEntity::getNickname).orElse(null);
        log.info("이메일 변경 링크를 만들었다 userId={}", userId);
        return new IssuedEmailChange(userId, user.getEmail(), newEmail, nickname, raw);
    }

    /** 낡은 세션(회원이 없거나 탈퇴)은 401 */
    private UserEntity lockMember(long userId) {
        return users.findWithLockById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }
}
