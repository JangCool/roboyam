package com.community.poll.repository.master;

import com.community.poll.dto.PollData;
import com.community.poll.dto.PollOptionData;
import com.community.poll.entity.PollEntity;
import com.community.poll.entity.PollOptionEntity;
import com.community.poll.entity.PollVoteEntity;
import com.community.poll.entity.QPollEntity;
import com.community.poll.entity.QPollOptionEntity;
import com.community.poll.entity.QPollVoteEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
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
import org.springframework.stereotype.Repository;

/**
 * 글 투표의 세 표(V81 {@code polls}·{@code poll_options}·{@code poll_votes}) — docs/features/post-poll-contract.md. <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>첫 표 잠금 규칙: 투표를 고치거나 지우는 쪽은 {@code polls} 행을 쓰기 잠금({@code FOR UPDATE} — {@link RowLocks}, 전환 전 세기), 투표하는 쪽은 공유 잠금
 * ({@code PESSIMISTIC_READ} = {@code FOR SHARE}) — 두 잠금은 서로 기다리고 투표끼리는 막지 않는다(고치는 도중에 표가 들어오지 않는다). 투표 행을 지우는 것은
 * {@code DELETE} 라 그 자체로 {@code FOR UPDATE} 세기를 잡는다. 한 사람의 동시 투표는 어드바이저리 잠금으로 줄을 세운다.
 * 읽기는 모두 프로젝션(엔티티를 올리지 않는다 — 대량 UPDATE·DELETE 뒤에 낡은 1차 캐시가 보이지 않게).
 * <b>익명</b>: 누가 무엇을 골랐는지는 {@link #myOptionIds}(그 사람 본인 것)로만 읽는다 — 사람별 목록을 내는 질의는 두지 않는다.
 */
@Repository
public class PollQueries extends RoboyamRepositoryQDSL {

    private static final QPollEntity POLL = QPollEntity.pollEntity;
    private static final QPollOptionEntity OPTION = QPollOptionEntity.pollOptionEntity;
    private static final QPollVoteEntity VOTE = QPollVoteEntity.pollVoteEntity;

    private final PollRepository polls;
    private final PollOptionRepository options;
    private final PollVoteRepository votes;
    private final EntityManager entityManager;

    public PollQueries(JPAQueryFactory query, PollRepository polls, PollOptionRepository options, PollVoteRepository votes, EntityManager entityManager) {
        super(query);
        this.polls = polls;
        this.options = options;
        this.votes = votes;
        this.entityManager = entityManager;
    }

    /** 그 글의 투표(잠그지 않음) */
    public Optional<PollData> find(long postId) {
        return Optional.ofNullable(query.select(Projections.constructor(PollData.class, POLL.id, POLL.postId, POLL.question, POLL.multiple, POLL.changeAllowed,
                POLL.closesAt, POLL.createdAt)).from(POLL).where(POLL.postId.eq(postId)).fetchOne());
    }

    /** 그 글의 투표 — 쓰기 잠금(고치기·지우기). 투표하는 쪽({@link #findForShare})과 서로 기다린다 */
    public Optional<PollData> findForUpdate(long postId) {
        // 전환 전 세기(FOR UPDATE — {@link RowLocks}): 글 번호로 고르고(잠금 없이) → 투표 행을 잠근다 → 다시 읽는다
        Long id = query.select(POLL.id).from(POLL).where(POLL.postId.eq(postId)).fetchFirst();
        if (id == null || !RowLocks.forUpdate(entityManager, PollEntity.class, id)) {
            return Optional.empty();
        }
        return locked(postId, LockModeType.NONE);
    }

    /** 그 글의 투표 — 공유 잠금(투표하기). 투표끼리는 막지 않는다 */
    public Optional<PollData> findForShare(long postId) {
        return locked(postId, LockModeType.PESSIMISTIC_READ);
    }

    /** 선택지 — 차례대로 */
    public List<PollOptionData> options(long pollId) {
        return query.select(OPTION.id, OPTION.position, OPTION.label).from(OPTION).where(OPTION.pollId.eq(pollId)).orderBy(OPTION.position.asc()).fetch().stream()
                .map(t -> new PollOptionData(t.get(OPTION.id), t.get(OPTION.position), t.get(OPTION.label))).toList();
    }

    /** 참여한 사람 수(복수 선택이어도 한 사람은 한 번) */
    public int voterCount(long pollId) {
        Long n = query.select(VOTE.userId.countDistinct()).from(VOTE).where(VOTE.pollId.eq(pollId)).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /** 표가 하나라도 있는가(첫 표 잠금) */
    public boolean hasVotes(long pollId) {
        return query.selectOne().from(VOTE).where(VOTE.pollId.eq(pollId)).fetchFirst() != null;
    }

    /** 선택지 ID → 득표 수(표가 없는 선택지는 빠진다) */
    public Map<Long, Integer> votesByOption(long pollId) {
        Map<Long, Integer> out = new HashMap<>();
        for (Tuple t : query.select(VOTE.optionId, VOTE.count()).from(VOTE).where(VOTE.pollId.eq(pollId)).groupBy(VOTE.optionId).fetch()) {
            out.put(t.get(VOTE.optionId), Math.toIntExact(t.get(VOTE.count())));
        }
        return out;
    }

    /** 그 사람이 고른 선택지 — 선택지 차례대로(투표 전이면 빈 목록) */
    public List<Long> myOptionIds(long pollId, long userId) {
        return query.select(VOTE.optionId).from(VOTE).join(OPTION).on(OPTION.id.eq(VOTE.optionId))
                .where(VOTE.pollId.eq(pollId), VOTE.userId.eq(userId)).orderBy(OPTION.position.asc()).fetch();
    }

    /** 이 글들 중 투표가 붙은 글(목록 줄의 {@code hasPoll}) */
    public Set<Long> postIdsWithPoll(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.select(POLL.postId).from(POLL).where(POLL.postId.in(postIds)).fetch());
    }

    /** 투표를 만든다(선택지는 {@link #insertOptions}) */
    public void insert(long pollId, long postId, String question, boolean multiple, boolean changeAllowed, Instant closesAt, Instant now) {
        polls.persist(PollEntity.create(pollId, postId, question, multiple, changeAllowed, closesAt, now));
    }

    /** 설정을 바꾼다(첫 표 전에만 — 부르는 쪽이 {@link #findForUpdate} 아래에서 확인한다) */
    public void update(long pollId, String question, boolean multiple, boolean changeAllowed, Instant closesAt, Instant now) {
        query.update(POLL).set(POLL.question, question).set(POLL.multiple, multiple).set(POLL.changeAllowed, changeAllowed).set(POLL.closesAt, closesAt)
                .set(POLL.updatedAt, now).where(POLL.id.eq(pollId)).execute();
    }

    /** 선택지를 차례대로 넣는다 — {@code ids} 와 {@code labels} 는 같은 길이 */
    public void insertOptions(long pollId, List<Long> ids, List<String> labels) {
        for (int i = 0; i < labels.size(); i++) {
            options.persist(PollOptionEntity.create(ids.get(i), pollId, i, labels.get(i)));
        }
    }

    /** 선택지를 모두 지운다(고치기 — 첫 표 전이라 지워질 표가 없다) */
    public void deleteOptions(long pollId) {
        query.delete(OPTION).where(OPTION.pollId.eq(pollId)).execute();
    }

    /** 투표를 지운다 — 선택지·표가 함께 사라진다(ON DELETE CASCADE) */
    public void delete(long pollId) {
        query.delete(POLL).where(POLL.id.eq(pollId)).execute();
    }

    /**
     * 한 사람의 이 투표 요청을 줄 세운다(트랜잭션 끝까지) — 같은 사람이 동시에 다른 선택을 보내도 한쪽 선택만 남는다.
     *
     * <p>Native SQL 인 이유: {@code pg_advisory_xact_lock} 은 PostgreSQL 함수다(잠글 행이 없다 — 처음 투표하는 사람의 줄은 아직 없다).
     */
    public void lockVoter(long pollId, long userId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(7340102, hashtext(?1))::text").setParameter(1, "poll:" + pollId + ":" + userId).getSingleResult();
    }

    /** 그 사람의 표를 이 선택으로 바꾼다(처음이면 넣기만) — {@link #lockVoter} 아래에서 */
    public void replaceVotes(long pollId, long userId, Collection<Long> optionIds, Instant now) {
        query.delete(VOTE).where(VOTE.pollId.eq(pollId), VOTE.userId.eq(userId)).execute();
        for (Long optionId : optionIds) {
            votes.persist(PollVoteEntity.create(pollId, userId, optionId, now));
        }
    }

    private Optional<PollData> locked(long postId, LockModeType mode) {
        PollEntity poll = query.selectFrom(POLL).where(POLL.postId.eq(postId)).setLockMode(mode).fetchOne();
        return Optional.ofNullable(poll).map(p -> new PollData(p.getId(), p.getPostId(), p.getQuestion(), p.isMultiple(), p.isChangeAllowed(), p.getClosesAt(),
                p.getCreatedAt()));
    }
}
