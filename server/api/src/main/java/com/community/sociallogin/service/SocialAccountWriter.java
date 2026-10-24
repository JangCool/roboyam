package com.community.sociallogin.service;

import com.community.auth.dto.AuthUser;
import com.community.auth.service.TermsVersionProvider;
import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.CallbackOutcome;
import com.community.sociallogin.dto.PendingSignup;
import com.community.sociallogin.dto.SocialFailure;
import com.community.sociallogin.dto.SocialMode;
import com.community.sociallogin.dto.SocialProfile;
import com.community.sociallogin.exception.SocialAlreadyRegisteredException;
import com.community.sociallogin.exception.SocialLastLoginMethodException;
import com.community.sociallogin.exception.SocialNotLinkedException;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.Role;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserIdentityEntity;
import com.community.user.exception.UserEmailTakenException;
import com.community.user.exception.UserNicknameTakenException;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.user.repository.master.UserIdentityRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인의 DB 일 — 메서드마다 짧은 트랜잭션이다(공급자와의 통신은 이 밖에서 끝난 뒤에 부른다).
 *
 * <ul>
 *   <li><b>연결된 소셜 계정</b>이면 그 회원으로 로그인한다(탈퇴·지운 회원은 거절, 정지·영구 정지는 기존 로그인 규칙대로 로그인되고 {@code restriction} 으로 알린다).</li>
 *   <li><b>처음 보는 소셜 계정</b>은 이메일이 기존 회원과 같으면 자동으로 연결하지 않는다(계정 탈취 방지). 겹치지 않으면 가입 완료 단계로 보낸다.</li>
 *   <li>한 회원에게 한 공급자의 계정은 하나까지다. 같은 소셜 계정은 한 회원에게만 연결된다(유일 제약이 마지막 방어선).</li>
 * </ul>
 * 로그에는 공급자·회원 ID 만 남긴다(이메일·공급자 회원 번호·닉네임 없음). 유일 제약 위반의 DB 문구에는 입력값이 실리므로 로그에 남기지 않는다.
 */
@Service
public class SocialAccountWriter {

    private static final Logger log = LoggerFactory.getLogger(SocialAccountWriter.class);

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileRepositoryQDSL profileQueries;
    private final UserIdentityRepository identities;
    private final SnowflakeIdGenerator ids;
    /** 가입 때 동의 받은 약관 버전 — 이메일 가입과 같은 한 곳({@link TermsVersionProvider#currentTermsVersion()})이 정한다 */
    private final TermsVersionProvider terms;

    public SocialAccountWriter(UserRepository users, ProfileRepository profiles, ProfileRepositoryQDSL profileQueries, UserIdentityRepository identities,
                               SnowflakeIdGenerator ids, TermsVersionProvider terms) {
        this.users = users;
        this.profiles = profiles;
        this.profileQueries = profileQueries;
        this.identities = identities;
        this.ids = ids;
        this.terms = terms;
    }

    /**
     * 로그인 흐름의 결과를 정한다. 연결된 회원이면 마지막 로그인 시각을 고친다.
     *
     * @param pendingExpiresAt 가입 완료 단계로 갈 때 그 대기 정보의 만료 시각
     */
    @Transactional
    public CallbackOutcome login(SocialProfile profile, String next, Instant pendingExpiresAt) {
        SocialProvider provider = profile.provider();
        Optional<UserIdentityEntity> identity = identities.findByProviderAndProviderUserId(provider, profile.subject());
        if (identity.isPresent()) {
            Optional<UserEntity> user = users.findById(identity.get().getUserId()).filter(u -> !u.isGone());
            if (user.isEmpty()) {
                log.info("소셜 로그인 거절 — 연결된 회원이 탈퇴했거나 없다 provider={}", provider);
                return CallbackOutcome.failed(provider, SocialMode.LOGIN, SocialFailure.ACCOUNT_UNAVAILABLE);
            }
            UserEntity member = user.get();
            member.markLoggedIn(Instant.now());
            log.info("소셜 로그인 provider={} userId={}", provider, member.getId());
            return CallbackOutcome.loggedIn(provider, new AuthUser(member.getId(), member.getRole()), next);
        }
        if (profile.email() != null && users.existsByEmail(profile.email())) {
            log.info("소셜 로그인 — 처음 보는 소셜 계정인데 이메일이 기존 회원과 같아 자동 연결하지 않았다 provider={}", provider);
            return CallbackOutcome.emailExists(provider);
        }
        log.info("소셜 로그인 — 처음 보는 소셜 계정, 가입 완료 단계로 provider={}", provider);
        return CallbackOutcome.signupRequired(provider, new PendingSignup(provider, profile.subject(), profile.email(), profile.emailVerified(),
                profile.nickname(), next, pendingExpiresAt));
    }

    /** 로그인한 회원({@code userId})에 소셜 계정을 연결한다 */
    @Transactional
    public CallbackOutcome link(long userId, SocialProfile profile) {
        SocialProvider provider = profile.provider();
        Optional<UserEntity> user = users.findWithLockById(userId).filter(u -> !u.isGone()); // 같은 회원의 연결·해제를 줄 세운다
        if (user.isEmpty()) {
            return CallbackOutcome.failed(provider, SocialMode.LINK, SocialFailure.ACCOUNT_UNAVAILABLE);
        }
        Optional<UserIdentityEntity> existing = identities.findByProviderAndProviderUserId(provider, profile.subject());
        if (existing.isPresent()) {
            if (existing.get().getUserId() == userId) {
                return CallbackOutcome.linked(provider); // 이미 이 회원에 연결돼 있다 — 같은 결과
            }
            log.info("소셜 연결 거절 — 다른 회원에 연결된 소셜 계정 provider={} userId={}", provider, userId);
            return CallbackOutcome.linkTaken(provider);
        }
        if (identities.findFirstByUserIdAndProvider(userId, provider).isPresent()) {
            return CallbackOutcome.failed(provider, SocialMode.LINK, SocialFailure.ALREADY_LINKED);
        }
        try {
            identities.persist(UserIdentityEntity.create(ids.nextId(), userId, provider, profile.subject(), profile.email(), Instant.now()));
            identities.flush();
        } catch (DataIntegrityViolationException e) {
            // 동시에 다른 회원이 같은 소셜 계정을 연결했다(유일 제약). 이 트랜잭션은 되돌린다
            throw new SocialLinkRaceException();
        }
        log.info("소셜 연결 provider={} userId={}", provider, userId);
        return CallbackOutcome.linked(provider);
    }

    /**
     * 가입 대기 정보로 회원을 만든다(비밀번호 없음, 약관·개인정보·만 14세 동의 = 지금). 이메일은 공급자가 확인했다고 한 경우만 인증 완료다.
     *
     * @throws SocialAlreadyRegisteredException 그 사이 그 소셜 계정이 회원에 연결됐다(409)
     * @throws UserEmailTakenException          그 사이 같은 이메일의 회원이 생겼다(409)
     * @throws UserNicknameTakenException       닉네임을 이미 쓰는 회원이 있다(409)
     */
    @Transactional
    public AuthUser createMember(PendingSignup pending, String nickname) {
        if (identities.findByProviderAndProviderUserId(pending.provider(), pending.subject()).isPresent()) {
            throw new SocialAlreadyRegisteredException();
        }
        if (pending.email() != null && users.existsByEmail(pending.email())) {
            throw new UserEmailTakenException();
        }
        if (profileQueries.existsByNickname(nickname)) {
            throw new UserNicknameTakenException();
        }
        Instant now = Instant.now();
        long userId = ids.nextId();
        try {
            users.persist(UserEntity.createSocial(userId, pending.email(), pending.emailVerified(), terms.currentTermsVersion(), now));
            profiles.persist(ProfileEntity.create(userId, nickname, now));
            identities.persist(UserIdentityEntity.create(ids.nextId(), userId, pending.provider(), pending.subject(), pending.email(), now));
            identities.flush();
        } catch (DataIntegrityViolationException e) {
            throw conflictOf(e); // 동시에 같은 닉네임·이메일·소셜 계정으로 가입했다
        }
        log.info("소셜 가입 provider={} userId={} emailVerified={}", pending.provider(), userId, pending.email() != null && pending.emailVerified());
        return new AuthUser(userId, Role.USER);
    }

    /**
     * 연결을 해제한다. 비밀번호가 없는 회원의 마지막 연결이면 거절한다.
     *
     * @throws SocialNotLinkedException       그 공급자가 연결돼 있지 않다(404)
     * @throws SocialLastLoginMethodException 마지막 로그인 수단이다(409)
     */
    @Transactional
    public void unlink(long userId, SocialProvider provider) {
        UserEntity user = users.findWithLockById(userId).filter(u -> !u.isGone())
                .orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        UserIdentityEntity identity = identities.findFirstByUserIdAndProvider(userId, provider).orElseThrow(SocialNotLinkedException::new);
        if (!user.hasPassword() && identities.findByUserIdOrderByCreatedAtAsc(userId).size() <= 1) {
            throw new SocialLastLoginMethodException();
        }
        identities.delete(identity);
        log.info("소셜 연결 해제 provider={} userId={}", provider, userId);
    }

    /** 유일 제약 이름으로 어느 칸이 겹쳤는지 가른다(DB 문구 자체는 입력값이 실려 로그·응답에 쓰지 않는다) */
    private static CommonException conflictOf(DataIntegrityViolationException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String text = cause.getMessage() == null ? "" : cause.getMessage();
        if (text.contains("ux_profiles_nickname")) {
            return new UserNicknameTakenException();
        }
        if (text.contains("ux_users_email")) {
            return new UserEmailTakenException();
        }
        if (text.contains("ux_user_identities_provider")) {
            return new SocialAlreadyRegisteredException();
        }
        log.error("소셜 가입 저장이 제약에 걸렸다 원인={}", cause.getClass().getSimpleName());
        return new CommonException(ErrorCode.INTERNAL);
    }

    /** 연결 저장이 동시에 다른 회원의 같은 연결과 겹쳤다 — 콜백 서비스가 「다른 회원에 연결됨」으로 돌려보낸다 */
    public static class SocialLinkRaceException extends RuntimeException {

        SocialLinkRaceException() {
            super("소셜 연결 경쟁", null, false, false);
        }
    }
}
