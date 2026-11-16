package com.community.pointgame.repository.master;

import com.community.point.entity.QPointBalanceEntity;
import com.community.pointgame.entity.LottoDrawEntity;
import com.community.pointgame.entity.QLottoDrawEntity;
import com.community.pointgame.entity.QRouletteSpinEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 포인트 게임 읽기·지우기(V234) — 룰렛 하루 횟수, 내 번호 목록·개수·지우기, 적립 잔액. 트랜잭션은 부르는 서비스가 연다.
 * 횟수·보관 개수는 회원 잔액 행 잠금({@code PointService#useFree} 의 {@code underLock}) 아래에서 세므로 같은 회원의 동시 요청이 한도를 넘지 못한다.
 */
@Repository
public class PointGameQueries extends IrobitRepositoryQDSL {

    private static final QRouletteSpinEntity S = QRouletteSpinEntity.rouletteSpinEntity;
    private static final QLottoDrawEntity D = QLottoDrawEntity.lottoDrawEntity;
    private static final QPointBalanceEntity B = QPointBalanceEntity.pointBalanceEntity;

    public PointGameQueries(JPAQueryFactory query) {
        super(query);
    }

    /** {@code since} 이후(포함) 그 회원이 돌린 룰렛 횟수 */
    public int spinsSince(long userId, Instant since) {
        Long n = query.select(S.id.count()).from(S).where(S.userId.eq(userId), S.createdAt.goe(since)).fetchOne();
        return n == null ? 0 : n.intValue();
    }

    /** 그 회원이 남겨 둔 번호 세트 수 */
    public int drawCount(long userId) {
        Long n = query.select(D.id.count()).from(D).where(D.userId.eq(userId)).fetchOne();
        return n == null ? 0 : n.intValue();
    }

    /** 내 번호 — 최신이 위(같은 시각이면 ID 큰 것 먼저), {@code limit} 개까지 */
    public List<LottoDrawEntity> draws(long userId, int limit) {
        return query.selectFrom(D).where(D.userId.eq(userId)).orderBy(D.createdAt.desc(), D.id.desc()).limit(limit).fetch();
    }

    /** 그 번호 줄의 주인(없으면 빈 값) */
    public Optional<Long> drawOwner(long drawId) {
        return Optional.ofNullable(query.select(D.userId).from(D).where(D.id.eq(drawId)).fetchOne());
    }

    /** 내 번호 한 줄 지우기 — 지운 줄 수(남의 줄·없는 줄은 0) */
    public long deleteDraw(long drawId, long userId) {
        return query.delete(D).where(D.id.eq(drawId), D.userId.eq(userId)).execute();
    }

    /** 적립(무상) 잔액 — 잔액 행이 없으면 0 */
    public long freeBalance(long userId) {
        Long free = query.select(B.freeBalance).from(B).where(B.userId.eq(userId)).fetchOne();
        return free == null ? 0 : free;
    }
}
