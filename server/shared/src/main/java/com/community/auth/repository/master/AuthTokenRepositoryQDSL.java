package com.community.auth.repository.master;

import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.entity.QAuthTokenEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 인증·재설정 토큰 질의. <b>트랜잭션은 부르는 서비스가 연다.</b> */
@Repository
public class AuthTokenRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QAuthTokenEntity TOKEN = QAuthTokenEntity.authTokenEntity;

    public AuthTokenRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 이 회원이 이 용도로 가장 최근에 받은 링크의 발급 시각(이미 쓴 것·만료된 것도 포함). 받은 적이 없으면 비어 있다 */
    public Optional<Instant> findLatestCreatedAt(long userId, AuthTokenPurpose purpose) {
        return Optional.ofNullable(query.select(TOKEN.createdAt).from(TOKEN)
                .where(TOKEN.userId.eq(userId), TOKEN.purpose.eq(purpose))
                .orderBy(TOKEN.createdAt.desc()).limit(1).fetchOne());
    }

    /** 토큰 해시의 주인(회원 ID)만 읽는다 — 엔티티를 올리지 않는다(회원 행을 먼저 잠근 뒤 토큰을 읽어야 할 때). 없으면 비어 있다 */
    public Optional<Long> findUserIdByHash(String tokenHash) {
        return Optional.ofNullable(query.select(TOKEN.userId).from(TOKEN).where(TOKEN.tokenHash.eq(tokenHash)).fetchOne());
    }

    /** 이 회원의 아직 쓰지 않았고 만료되지 않은 가장 최근 이메일 변경 링크의 새 주소. 없으면 비어 있다 */
    public Optional<String> findPendingNewEmail(long userId, Instant now) {
        return Optional.ofNullable(query.select(TOKEN.newEmail).from(TOKEN)
                .where(TOKEN.userId.eq(userId), TOKEN.purpose.eq(AuthTokenPurpose.CHANGE_EMAIL), TOKEN.consumedAt.isNull(), TOKEN.expiresAt.gt(now))
                .orderBy(TOKEN.createdAt.desc()).limit(1).fetchOne());
    }

    /** 아직 쓰지 않은 링크를 모두 사용 처리해 무효로 한다(새 링크를 만들 때 — 가장 최근 것만 쓸 수 있다). 처리한 개수를 돌려준다 */
    public long consumeAll(long userId, AuthTokenPurpose purpose, Instant now) {
        return query.update(TOKEN).set(TOKEN.consumedAt, now)
                .where(TOKEN.userId.eq(userId), TOKEN.purpose.eq(purpose), TOKEN.consumedAt.isNull())
                .execute();
    }
}
