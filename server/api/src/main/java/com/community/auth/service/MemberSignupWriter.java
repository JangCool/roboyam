package com.community.auth.service;

import com.community.auth.dto.AuthUser;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.Role;
import com.community.user.entity.UserEntity;
import com.community.user.exception.UserEmailTakenException;
import com.community.user.exception.UserNicknameTakenException;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 가입의 DB 일 — 메서드마다 짧은 트랜잭션이다(비밀번호 해시는 이 밖에서 끝낸 뒤 부른다). 회원·프로필을 만드는 방식은 소셜 가입({@code SocialAccountWriter#createMember})과 같다
 * — {@code users}(비밀번호 있음, 이메일 인증 전) + {@code profiles}(기본 등급).
 * 이메일·닉네임 중복은 409 이고, 동시에 같은 값으로 가입하면 유일 제약({@code ux_users_email}·{@code ux_profiles_nickname})이 마지막 방어선이다 — 같은 409 로 바꾼다.
 * 로그에는 회원 ID 만 남긴다(유일 제약 위반의 DB 문구에는 입력값이 실리므로 쓰지 않는다).
 */
@Service
public class MemberSignupWriter {

    private static final Logger log = LoggerFactory.getLogger(MemberSignupWriter.class);

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileRepositoryQDSL profileQueries;
    private final SnowflakeIdGenerator ids;

    public MemberSignupWriter(UserRepository users, ProfileRepository profiles, ProfileRepositoryQDSL profileQueries, SnowflakeIdGenerator ids) {
        this.users = users;
        this.profiles = profiles;
        this.profileQueries = profileQueries;
        this.ids = ids;
    }

    /**
     * 이메일·닉네임을 쓸 수 있는지 미리 본다(느린 비밀번호 해시 전에 거르려는 것 — 저장 때 다시 본다).
     *
     * @throws UserEmailTakenException    이미 그 이메일의 회원이 있다(409)
     * @throws UserNicknameTakenException 이미 그 닉네임의 회원이 있다(409, 대소문자 무시)
     */
    @Transactional(readOnly = true)
    public void assertAvailable(String email, String nickname) {
        if (users.existsByEmail(email)) {
            throw new UserEmailTakenException();
        }
        if (profileQueries.existsByNickname(nickname)) {
            throw new UserNicknameTakenException();
        }
    }

    /**
     * 회원을 만든다(약관·개인정보·만 14세 동의 = 지금, 마지막 로그인 = 지금).
     *
     * @param email        정규화한 이메일
     * @param passwordHash bcrypt 해시
     * @param termsVersion 동의한 약관 버전({@link TermsVersionProvider#currentTermsVersion()})
     */
    @Transactional
    public AuthUser create(String email, String passwordHash, String nickname, String termsVersion) {
        assertAvailable(email, nickname);
        Instant now = Instant.now();
        long userId = ids.nextId();
        try {
            users.persist(UserEntity.createWithPassword(userId, email, passwordHash, termsVersion, now));
            profiles.persist(ProfileEntity.create(userId, nickname, now));
            users.flush();
        } catch (DataIntegrityViolationException e) {
            throw conflictOf(e); // 동시에 같은 이메일·닉네임으로 가입했다
        }
        log.info("이메일 가입 userId={}", userId);
        return new AuthUser(userId, Role.USER);
    }

    /** 유일 제약 이름으로 어느 칸이 겹쳤는지 가른다(DB 문구 자체는 입력값이 실려 로그·응답에 쓰지 않는다) */
    private static CommonException conflictOf(DataIntegrityViolationException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String text = cause.getMessage() == null ? "" : cause.getMessage();
        if (text.contains("ux_users_email")) {
            return new UserEmailTakenException();
        }
        if (text.contains("ux_profiles_nickname")) {
            return new UserNicknameTakenException();
        }
        log.error("이메일 가입 저장이 제약에 걸렸다 원인={}", cause.getClass().getSimpleName());
        return new CommonException(ErrorCode.INTERNAL);
    }
}
