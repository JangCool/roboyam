package com.community.testsupport;

import com.community.user.entity.ProfileEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.context.ApplicationContext;

/**
 * 시험 회원({@code users} + {@code profiles}) — 엔티티 정적 팩터리·도메인 메서드로 만들고 {@code persist()} 로 넣는다(호출마다 커밋).
 *
 * <ul>
 *   <li>회원은 소셜 가입 팩터리({@link UserEntity#createSocial})로 만든다 — 비밀번호 없음·활성·약관 {@value #TERMS_VERSION}·만 14세 확인 = 지금.
 *       팩터리가 마지막 로그인 시각도 지금으로 둔다(예전 SQL 은 NULL — 로그인 기록이 없는 회원을 만드는 팩터리가 없다).</li>
 *   <li>프로필은 기본 등급(새싹)으로 만들고, 등급을 고르면 자동 승급(수동 표시 없음)으로 올린다 — 예전 SQL 의 {@code grade_key} 칸 지정과 같은 행이다.</li>
 *   <li>상태(정지·차단·탈퇴)·탈퇴 시각을 바꾸는 도메인 메서드는 엔티티에 없다 — 그 칸은 시험이 SQL 로 바꾼다.</li>
 * </ul>
 */
public final class MemberFixture {

    /** 시험 회원이 동의한 약관 버전 */
    public static final String TERMS_VERSION = "v1";

    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;

    private final FixtureTx tx;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final JPAQueryFactory query;

    public MemberFixture(ApplicationContext context) {
        this.tx = new FixtureTx(context);
        this.users = context.getBean(UserRepository.class);
        this.profiles = context.getBean(ProfileRepository.class);
        this.query = context.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
    }

    /** 활성 회원(프로필 없음) — 이메일 미인증 */
    public void user(long id, String email) {
        user(id, email, false);
    }

    /** 활성 회원(프로필 없음) — {@code emailVerified} 면 이메일 인증 = 지금 */
    public void user(long id, String email, boolean emailVerified) {
        tx.run(() -> users.persist(UserEntity.createSocial(id, email, emailVerified, TERMS_VERSION, FixtureTx.now())));
    }

    /** 이메일 인증을 마친 것으로 둔다(이미 했으면 그대로) */
    public void verifyEmail(long userId) {
        tx.run(() -> users.findById(userId).orElseThrow().markEmailVerified(FixtureTx.now()));
    }

    /** 기본 등급(새싹) 프로필 */
    public void profile(long userId, String nickname) {
        tx.run(() -> profiles.persist(ProfileEntity.create(userId, nickname, FixtureTx.now())));
    }

    /** 고른 등급의 프로필 — 자동 승급으로 올린 것과 같다(수동 표시 없음) */
    public void profile(long userId, String nickname, String gradeKey) {
        tx.run(() -> {
            ProfileEntity profile = ProfileEntity.create(userId, nickname, FixtureTx.now());
            profile.promoteAutomatically(gradeKey, FixtureTx.now());
            profiles.persist(profile);
        });
    }

    /** 프로필이 없을 때만 만든다(이미 있으면 그대로) — 예전 {@code ON CONFLICT DO NOTHING} 자리 */
    public void profileIfAbsent(long userId, String nickname) {
        tx.run(() -> {
            if (!profiles.existsById(userId)) {
                profiles.persist(ProfileEntity.create(userId, nickname, FixtureTx.now()));
            }
        });
    }

    /** 있는 프로필의 등급을 바꾼다(자동 승급 — 수동 표시 없음) */
    public void grade(long userId, String gradeKey) {
        tx.run(() -> profiles.findById(userId).orElseThrow().promoteAutomatically(gradeKey, FixtureTx.now()));
    }

    /** 회원 상태(ACTIVE·SUSPENDED·…·WITHDRAWN) */
    public String status(long userId) {
        return tx.call(() -> {
            Object status = query.select(USER.status).from(USER).where(USER.id.eq(userId)).fetchOne();
            return status == null ? null : status.toString();
        });
    }

    /** 회원 이메일(탈퇴하면 null) */
    public String email(long userId) {
        return tx.call(() -> query.select(USER.email).from(USER).where(USER.id.eq(userId)).fetchOne());
    }

    /** 닉네임(프로필이 없으면 null) */
    public String nickname(long userId) {
        return tx.call(() -> query.select(PROFILE.nickname).from(PROFILE).where(PROFILE.userId.eq(userId)).fetchOne());
    }
}
