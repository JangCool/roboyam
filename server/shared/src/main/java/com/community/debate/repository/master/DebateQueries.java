package com.community.debate.repository.master;

import com.community.debate.dto.DebateVotesView;
import com.community.debate.entity.DebateStance;
import com.community.debate.entity.QDebatePinEntity;
import com.community.debate.entity.QPostStanceEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

/**
 * 토론의 작은 표 둘(V76·V78) — 회원의 글별 현재 입장({@code post_stances} — 투표)과 운영자가 고정한 의견({@code debate_pins}). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 * 읽기는 프로젝션(바꾸기는 조건부 대량 UPDATE 라 엔티티를 올려 두지 않는다).
 *
 * <p>Native SQL 로 남긴 것: 처음 입장을 고르는(투표) 요청이 동시에 둘 들어와도 한 행만 남아야 해서 {@code INSERT … ON CONFLICT DO NOTHING}
 * (실패한 문장이 트랜잭션을 망가뜨리지 않게), 고정도 같은 방식(멱등), 토론 열기({@code post_debates} — 엔티티 {@code PostDebateEntity} 의 나머지 문장은 {@code PostDebateRepository})도 같은 방식. 입장 바꾸기의 의견 소급({@code WITH moved AS (UPDATE … RETURNING)} — 바꾼 줄 중 지우지 않은 것의 수를 한 문장으로).
 */
@Repository
public class DebateQueries extends RoboyamRepositoryQDSL {

    private static final QPostStanceEntity STANCE = QPostStanceEntity.postStanceEntity;
    private static final QDebatePinEntity PIN = QDebatePinEntity.debatePinEntity;

    private final EntityManager entityManager;

    public DebateQueries(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 그 회원의 그 글 현재 입장 */
    public Optional<DebateStance> stanceOf(long postId, long userId) {
        return Optional.ofNullable(query.select(STANCE.stance).from(STANCE).where(STANCE.postId.eq(postId), STANCE.userId.eq(userId)).fetchOne());
    }

    /** 그 회원의 그 글 현재 입장 — 행을 쓰기 잠금으로 읽는다(입장 바꾸기가 줄을 선다). 없으면 빈 값(잠글 행이 없다 — {@link #insertIfAbsent} 가 경합을 막는다) */
    public Optional<DebateStance> lockStance(long postId, long userId) {
        // 잠금은 입장 표만 고르는 키 문장(PESSIMISTIC_WRITE = FOR NO KEY UPDATE — post_stances 를 FK 로 가리키는 표가 없다)
        if (query.select(STANCE.postId).from(STANCE).where(STANCE.postId.eq(postId), STANCE.userId.eq(userId)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne() == null) {
            return Optional.empty();
        }
        return stanceOf(postId, userId); // 값은 프로젝션으로 — 엔티티를 1차 캐시에 올리지 않는다(뒤의 대량 UPDATE {@link #change})
    }

    /**
     * 처음 입장을 고른다(투표) — 이미 행이 있으면(동시에 먼저 골랐으면) 아무것도 하지 않고 false.
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING} — 「없으면 넣기」를 두 문장으로 나누면 동시 요청에서 {@code duplicate key} 로 트랜잭션이 깨진다.
     */
    public boolean insertIfAbsent(long postId, long userId, DebateStance stance, Instant now) {
        return entityManager.createNativeQuery("INSERT INTO post_stances (post_id, user_id, stance, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?4) ON CONFLICT DO NOTHING")
                .setParameter(1, postId).setParameter(2, userId).setParameter(3, stance.name()).setParameter(4, now).executeUpdate() > 0;
    }

    /**
     * 토론을 연다({@code post_debates} 행 만들기) — 이미 열려 있으면(동시에 먼저 열었으면) 아무것도 하지 않고 false. 만든 쪽이 처음 연 사람으로 남는다.
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING} — 같은 글을 동시에 열어도 한 행만 남고 늦은 쪽은 {@code duplicate key} 로 트랜잭션이 깨지지 않고 설정 바꾸기로 이어 간다
     * ({@code PostDebateWriter}).
     */
    public boolean openIfAbsent(long postId, boolean neutralEnabled, boolean stanceChangeAllowed, String byKind, Long by, Instant now) {
        return entityManager.createNativeQuery("INSERT INTO post_debates (post_id, neutral_enabled, stance_change_allowed, opened_by_kind, opened_by, created_at, updated_at, "
                        + "updated_by_kind, updated_by) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6, ?4, ?5) ON CONFLICT DO NOTHING")
                .unwrap(NativeQuery.class).setParameter(1, postId).setParameter(2, neutralEnabled).setParameter(3, stanceChangeAllowed).setParameter(4, byKind)
                .setParameter(5, by, Long.class).setParameter(6, now).executeUpdate() > 0;
    }

    /** 글 하나의 투표 수(입장을 고른 사람 수) */
    public DebateVotesView votesOf(long postId) {
        return votesOf(List.of(postId)).getOrDefault(postId, DebateVotesView.EMPTY);
    }

    /** 글 여럿의 투표 수 — 글 ID → 수(투표가 없는 글은 {@link DebateVotesView#EMPTY}) */
    public Map<Long, DebateVotesView> votesOf(Collection<Long> postIds) {
        Map<Long, DebateVotesView> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        Map<Long, int[]> acc = new HashMap<>();
        for (Tuple t : query.select(STANCE.postId, STANCE.stance, STANCE.count()).from(STANCE).where(STANCE.postId.in(postIds)).groupBy(STANCE.postId, STANCE.stance)
                .fetch()) {
            int[] c = acc.computeIfAbsent(t.get(STANCE.postId), k -> new int[3]);
            c[t.get(STANCE.stance).ordinal()] += Math.toIntExact(t.get(STANCE.count()));
        }
        for (Long postId : postIds) {
            int[] c = acc.get(postId);
            out.put(postId, c == null ? DebateVotesView.EMPTY : DebateVotesView.of(c[DebateStance.PRO.ordinal()], c[DebateStance.CON.ordinal()],
                    c[DebateStance.NEUTRAL.ordinal()]));
        }
        return out;
    }

    /** 입장을 바꾼다 → 바꾼 줄 수(입장이 없으면 0) */
    public int change(long postId, long userId, DebateStance stance, Instant now) {
        return (int) query.update(STANCE).set(STANCE.stance, stance).set(STANCE.updatedAt, now).where(STANCE.postId.eq(postId), STANCE.userId.eq(userId)).execute();
    }

    /**
     * 입장 바꾸기의 소급 — 그 회원이 그 글에 쓴 <b>입장 있는 댓글</b>(원댓글·답글, 숨긴 것·지운 것 포함)의 배지를 새 입장으로 바꾼다. 일반 댓글(배지 없음)은 그대로.
     * 지운 댓글도 함께 바꿔 답글이 달린 지운 자리가 옛 입장 탭에 남지 않게 한다. 고정({@code debate_pins})·숨김 칸은 건드리지 않는다.
     *
     * <p>Native SQL 인 이유: 바꾼 줄 중 지우지 않은 것의 수가 <b>바꾼 그 문장의 결과</b>여야 한다({@code UPDATE … RETURNING} + {@code FILTER} 집계 — 두 문장으로 나누면
     * 사이에 다른 요청이 댓글을 지우거나 되살릴 수 있다).
     *
     * @return 바뀐 의견 중 <b>지우지 않은 것</b>의 수(공개 목록 {@code debate.myOpinionCount} 와 같은 기준)
     */
    public int moveOpinions(long postId, long userId, DebateStance stance) {
        Object moved = entityManager.createNativeQuery("""
                        WITH moved AS (
                            UPDATE comments SET stance = ?1
                             WHERE post_id = ?2 AND author_id = ?3 AND stance IS NOT NULL AND stance <> ?1
                            RETURNING deleted_at
                        )
                        SELECT count(*) FILTER (WHERE deleted_at IS NULL) FROM moved
                        """).setParameter(1, stance.name()).setParameter(2, postId).setParameter(3, userId).getSingleResult();
        return moved == null ? 0 : ((Number) moved).intValue();
    }

    /** 그 글에서 고정한 의견(댓글 ID) */
    public Set<Long> pinnedOf(long postId) {
        return new HashSet<>(query.select(PIN.commentId).from(PIN).where(PIN.postId.eq(postId)).fetch());
    }

    /** 그 글에서 고정한 의견(댓글 ID)을 <b>고정한 시각 순</b>(같으면 댓글 ID 순)으로 — 공개 댓글 목록의 토론 의견 맨 위 차례 */
    public List<Long> pinnedInOrder(long postId) {
        return query.select(PIN.commentId).from(PIN).where(PIN.postId.eq(postId)).orderBy(PIN.createdAt.asc(), PIN.commentId.asc()).fetch();
    }

    /**
     * 고정 — 이미 고정이면 그대로. {@code operatorId} 는 고정한 운영자(회원이 고정 요청을 승인했으면 null — 누가 승인했는지는 요청 행에 남는다).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING}(멱등 — 동시에 두 번 고정해도 처음 시각 한 줄).
     */
    public void pin(long commentId, long postId, Long operatorId, Instant now) {
        entityManager.createNativeQuery("INSERT INTO debate_pins (comment_id, post_id, pinned_by, created_at) VALUES (?1, ?2, ?3, ?4) ON CONFLICT DO NOTHING")
                .unwrap(NativeQuery.class).setParameter(1, commentId).setParameter(2, postId).setParameter(3, operatorId, Long.class).setParameter(4, now).executeUpdate();
    }

    /** 고정 해제 — 고정이 아니면 그대로 */
    public void unpin(long commentId) {
        query.delete(PIN).where(PIN.commentId.eq(commentId)).execute();
    }
}
