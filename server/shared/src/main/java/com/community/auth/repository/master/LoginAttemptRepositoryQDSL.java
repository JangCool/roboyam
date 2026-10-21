package com.community.auth.repository.master;

import com.community.auth.entity.LoginAttemptEntity;
import com.community.auth.entity.QLoginAttemptEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 로그인 실패 횟수 행의 생성(upsert)·잠금 읽기·잠금 시각 읽기. <b>트랜잭션은 부르는 서비스가 연다</b> — 행 잠금은 트랜잭션 안에서만 의미가 있다.
 */
@Repository
public class LoginAttemptRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QLoginAttemptEntity ATTEMPT = QLoginAttemptEntity.loginAttemptEntity;

    private final EntityManager entityManager;

    public LoginAttemptRepositoryQDSL(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /**
     * 행이 없으면 만든다(동시에 여러 요청이 와도 하나만 생긴다). 이미 있으면 아무것도 하지 않는다.
     *
     * <p>Native SQL 인 이유: PostgreSQL 의 {@code INSERT ... ON CONFLICT DO NOTHING} 은 JPQL·QueryDSL 에 대응 문법이 없다.
     * 「찾아보고 없으면 넣는다」로 바꾸면 두 문장 사이에 경합이 생겨 {@code duplicate key} 로 깨진다.
     */
    public void insertIfAbsent(String emailKey, Instant now) {
        entityManager.createNativeQuery(
                        "insert into login_attempts (email_key, fail_count, updated_at) values (?1, 0, ?2) on conflict (email_key) do nothing")
                .setParameter(1, emailKey)
                .setParameter(2, now)
                .executeUpdate();
    }

    /** 같은 이메일로 동시에 실패해도 횟수가 겹치지 않도록 행을 <b>잠그고</b> 읽는다({@code FOR NO KEY UPDATE}) */
    public Optional<LoginAttemptEntity> findForUpdate(String emailKey) {
        return Optional.ofNullable(query.selectFrom(ATTEMPT).where(ATTEMPT.emailKey.eq(emailKey))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
    }

    /** 잠금이 끝나는 시각. 한 번도 잠기지 않았거나 행이 없으면 비어 있다(이미 지난 시각이 올 수도 있다 — 부르는 쪽이 지금과 견준다) */
    public Optional<Instant> findLockedUntil(String emailKey) {
        return Optional.ofNullable(query.select(ATTEMPT.lockedUntil).from(ATTEMPT).where(ATTEMPT.emailKey.eq(emailKey)).fetchOne());
    }
}
