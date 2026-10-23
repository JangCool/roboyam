package com.community.auth.service;

import com.community.auth.config.AuthProperties;
import com.community.auth.dto.IssuedVerification;
import com.community.auth.entity.AuthTokenEntity;
import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.exception.AuthAlreadyVerifiedException;
import com.community.auth.exception.AuthResendCooldownException;
import com.community.auth.repository.master.AuthTokenRepository;
import com.community.auth.repository.master.AuthTokenRepositoryQDSL;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증 링크(토큰)를 만든다 — DB 일만 하는 짧은 트랜잭션이다. 메일 발송은 트랜잭션이 끝난 뒤 {@link EmailVerificationService} 가 한다.
 *
 * <ul>
 *   <li>토큰은 무작위 256비트이고 <b>해시(SHA-256)만 저장</b>한다. 유효 24시간.</li>
 *   <li>새 링크를 만들면 이전 링크는 모두 무효가 된다(가장 최근 것만 쓸 수 있다).</li>
 *   <li>재발송은 <b>60초에 한 번</b> — 가장 최근에 만든 링크 기준이다(이미 쓴 것·만료된 것도 센다). 회원 행을 잠가 같은 회원의 동시 요청을 줄 세운다.</li>
 * </ul>
 */
@Service
public class VerificationTokenService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AuthTokenRepository tokens;
    private final AuthTokenRepositoryQDSL tokenQueries;
    private final SnowflakeIdGenerator ids;
    private final AuthProperties props;

    public VerificationTokenService(UserRepository users, ProfileRepository profiles, AuthTokenRepository tokens,
                                    AuthTokenRepositoryQDSL tokenQueries, SnowflakeIdGenerator ids, AuthProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.tokens = tokens;
        this.tokenQueries = tokenQueries;
        this.ids = ids;
        this.props = props;
    }

    /**
     * 로그인한 회원의 새 인증 링크를 만든다.
     *
     * @throws CommonException                회원이 없거나 탈퇴했을 때(401 UNAUTHENTICATED — 낡은 세션)
     * @throws AuthAlreadyVerifiedException   이미 인증됐을 때(409)
     * @throws AuthResendCooldownException    60초가 안 지났을 때(429)
     */
    @Transactional
    public IssuedVerification issue(long userId) {
        UserEntity user = users.findWithLockById(userId).filter(u -> !u.isGone())
                .orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (user.isEmailVerified() || user.getEmail() == null) {
            throw new AuthAlreadyVerifiedException();
        }

        Instant now = Instant.now();
        long cooldownMillis = Duration.ofSeconds(props.resendCooldownSeconds()).toMillis();
        tokenQueries.findLatestCreatedAt(userId, AuthTokenPurpose.VERIFY_EMAIL).ifPresent(latest -> {
            long remainingMillis = Math.min(cooldownMillis, cooldownMillis - Duration.between(latest, now).toMillis());
            if (remainingMillis > 0) {
                throw new AuthResendCooldownException((remainingMillis + 999) / 1000);
            }
        });

        tokenQueries.consumeAll(userId, AuthTokenPurpose.VERIFY_EMAIL, now);
        String raw = AuthTokens.newToken();
        tokens.persist(AuthTokenEntity.create(ids.nextId(), userId, AuthTokenPurpose.VERIFY_EMAIL, AuthTokens.sha256Hex(raw),
                now.plus(Duration.ofHours(props.verifyTokenHours())), now));

        String nickname = profiles.findById(userId).map(ProfileEntity::getNickname).orElse(null); // 없으면 메일 본문이 요청 언어의 기본 호칭을 쓴다
        return new IssuedVerification(user.getEmail(), nickname, raw, !user.hasPassword());
    }
}
