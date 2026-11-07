package com.community.withdrawal.repository.master;

import com.community.withdrawal.entity.IdentityWithdrawalEntity;
import com.community.withdrawal.entity.QIdentityWithdrawalEntity;
import com.community.withdrawal.entity.WithdrawalKind;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 재가입·재인증 제한 해시 표({@code identity_withdrawals}, shared V84 — {@link IdentityWithdrawalEntity}) — {@code IdentityWithdrawalRecorder} 와 본인인증 확정이 부른다.
 * 해시만 다룬다(누구의 것인지 잇는 칸이 없다). <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class IdentityWithdrawalStore extends RoboyamRepositoryQDSL {

    private static final QIdentityWithdrawalEntity I = QIdentityWithdrawalEntity.identityWithdrawalEntity;

    private final IdentityWithdrawalRepository repository;

    public IdentityWithdrawalStore(JPAQueryFactory query, IdentityWithdrawalRepository repository) {
        super(query);
        this.repository = repository;
    }

    /** 넣는다 — 그 자리에서 INSERT 한다({@code persistAndFlush}, 탈퇴 정리의 문장 차례를 예전과 같게) */
    public void insert(long id, WithdrawalKind kind, String hash, Instant withdrawnAt, Instant expiresAt) {
        repository.persistAndFlush(IdentityWithdrawalEntity.of(id, kind, hash, withdrawnAt, expiresAt));
    }

    /** 그 종류·해시가 {@code now} 뒤까지 막혀 있으면 가장 늦은 끝(색인 {@code ix_identity_withdrawals_lookup}) */
    public Optional<Instant> blockedUntil(WithdrawalKind kind, String hash, Instant now) {
        if (hash == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(query.select(I.expiresAt.max()).from(I).where(I.kind.eq(kind), I.hash.eq(hash), I.expiresAt.gt(now)).fetchOne());
    }
}
