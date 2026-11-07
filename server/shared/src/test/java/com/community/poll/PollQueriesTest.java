package com.community.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.poll.dto.PollData;
import com.community.poll.dto.PollOptionData;
import com.community.poll.repository.master.PollQueries;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MemberFixture;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글 투표 저장소({@link PollQueries}) — JPA·QueryDSL 전환 뒤에도 예전 SQL 과 같은 결과·차례·잠금(docs/features/post-poll-contract.md). 실제 PostgreSQL.
 * 고치는 쪽 쓰기 잠금({@code FOR NO KEY UPDATE})과 투표하는 쪽 공유 잠금({@code FOR SHARE})은 서로 기다리고, 공유 잠금끼리는 막지 않는다.
 */
class PollQueriesTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(941);

    private static PollQueries polls;
    private static TransactionTemplate tx;
    private static JdbcTemplate jdbc;
    private static MemberFixture members;

    @BeforeAll
    static void setUp() {
        polls = JpaTestContext.create(PollQueries.class);
        tx = new TransactionTemplate(JpaTestContext.transactions());
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        members = new MemberFixture(JpaTestContext.context());
    }

    private static long member() {
        long id = IDS.nextId();
        members.user(id, "poll-" + id + "@test.local");
        return id;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 투표 하나(선택지 셋) — 투표 ID */
    private static long poll(long postId, boolean multiple, Instant closesAt) {
        long pollId = IDS.nextId();
        tx.executeWithoutResult(s -> {
            polls.insert(pollId, postId, "점심은?", multiple, false, closesAt, now());
            polls.insertOptions(pollId, List.of(IDS.nextId(), IDS.nextId(), IDS.nextId()), List.of("국밥", "라면", "김밥"));
        });
        return pollId;
    }

    @Test
    void 만들고_읽으면_칸과_선택지_차례가_그대로다() {
        long postId = IDS.nextId();
        Instant closes = now().plusSeconds(3600);
        long pollId = poll(postId, true, closes);

        PollData read = polls.find(postId).orElseThrow();
        assertThat(read.id()).isEqualTo(pollId);
        assertThat(read.postId()).isEqualTo(postId);
        assertThat(read.question()).isEqualTo("점심은?");
        assertThat(read.multiple()).isTrue();
        assertThat(read.changeAllowed()).isFalse();
        assertThat(read.closesAt()).isEqualTo(closes);
        assertThat(polls.options(pollId)).extracting(PollOptionData::position, PollOptionData::label)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0, "국밥"), org.assertj.core.groups.Tuple.tuple(1, "라면"), org.assertj.core.groups.Tuple.tuple(2, "김밥"));
        assertThat(polls.find(IDS.nextId())).isEmpty();
        assertThat(polls.postIdsWithPoll(List.of(postId, IDS.nextId()))).containsExactly(postId);
        assertThat(polls.postIdsWithPoll(List.of())).isEmpty();
        assertThat(polls.hasVotes(pollId)).isFalse();
        assertThat(polls.voterCount(pollId)).isZero();
    }

    @Test
    void 고치기는_한_트랜잭션에서_설정을_바꾸고_선택지를_다시_넣는다() {
        long postId = IDS.nextId();
        long pollId = poll(postId, false, null);
        tx.executeWithoutResult(s -> {
            PollData locked = polls.findForUpdate(postId).orElseThrow();
            polls.update(locked.id(), "저녁은?", true, true, null, now());
            polls.deleteOptions(locked.id());
            polls.insertOptions(locked.id(), List.of(IDS.nextId(), IDS.nextId()), List.of("피자", "치킨"));
            // 같은 트랜잭션의 읽기가 바꾼 값을 본다(대량 UPDATE 뒤 낡은 캐시가 아니라)
            assertThat(polls.find(postId).orElseThrow().question()).isEqualTo("저녁은?");
            assertThat(polls.options(locked.id())).extracting(PollOptionData::label).containsExactly("피자", "치킨");
        });
        PollData after = polls.find(postId).orElseThrow();
        assertThat(after.question()).isEqualTo("저녁은?");
        assertThat(after.multiple()).isTrue();
        assertThat(after.changeAllowed()).isTrue();
        assertThat(polls.options(pollId)).extracting(PollOptionData::position).containsExactly(0, 1);
    }

    @Test
    void 표를_바꾸면_그_사람_줄만_바뀌고_수는_사람_단위로_센다() {
        long postId = IDS.nextId();
        long pollId = poll(postId, true, null);
        List<Long> options = polls.options(pollId).stream().map(PollOptionData::id).toList();
        long a = member();
        long b = member();
        tx.executeWithoutResult(s -> {
            polls.lockVoter(pollId, a);
            polls.replaceVotes(pollId, a, List.of(options.get(2), options.get(0)), now());
        });
        tx.executeWithoutResult(s -> polls.replaceVotes(pollId, b, List.of(options.get(0)), now()));

        assertThat(polls.myOptionIds(pollId, a)).as("선택지 차례").containsExactly(options.get(0), options.get(2));
        assertThat(polls.voterCount(pollId)).isEqualTo(2);
        assertThat(polls.votesByOption(pollId)).isEqualTo(Map.of(options.get(0), 2, options.get(2), 1));
        assertThat(polls.hasVotes(pollId)).isTrue();

        tx.executeWithoutResult(s -> polls.replaceVotes(pollId, a, List.of(options.get(1)), now()));
        assertThat(polls.myOptionIds(pollId, a)).containsExactly(options.get(1));
        assertThat(polls.votesByOption(pollId)).isEqualTo(Map.of(options.get(0), 1, options.get(1), 1));
        assertThat(polls.myOptionIds(pollId, member())).isEmpty();
    }

    @Test
    void 투표를_지우면_선택지와_표가_함께_사라진다() {
        long postId = IDS.nextId();
        long pollId = poll(postId, false, null);
        long voter = member();
        long option = polls.options(pollId).getFirst().id();
        tx.executeWithoutResult(s -> polls.replaceVotes(pollId, voter, List.of(option), now()));
        tx.executeWithoutResult(s -> polls.delete(polls.findForUpdate(postId).orElseThrow().id()));
        assertThat(polls.find(postId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poll_options WHERE poll_id = ?", Integer.class, pollId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poll_votes WHERE poll_id = ?", Integer.class, pollId)).isZero();
    }

    @Test
    void 쓰기_잠금은_공유_잠금을_기다리게_하고_공유_잠금끼리는_막지_않는다() throws Exception {
        long postId = IDS.nextId();
        poll(postId, false, null);

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            polls.findForUpdate(postId).orElseThrow();
            held.countDown();
            await(release);
        }));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                lockTimeout();
                polls.findForShare(postId);
            })).as("고치는 동안 투표하는 쪽은 기다린다").isInstanceOf(PersistenceException.class);
        } finally {
            release.countDown();
            writer.get(10, TimeUnit.SECONDS);
        }

        CountDownLatch shared = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        CompletableFuture<Void> voter = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            polls.findForShare(postId).orElseThrow();
            shared.countDown();
            await(done);
        }));
        assertThat(shared.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            tx.executeWithoutResult(s -> {
                lockTimeout();
                assertThat(polls.findForShare(postId)).as("투표끼리는 막지 않는다").isPresent();
            });
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                lockTimeout();
                polls.findForUpdate(postId);
            })).as("표를 받는 동안 고치는 쪽은 기다린다").isInstanceOf(PersistenceException.class);
        } finally {
            done.countDown();
            voter.get(10, TimeUnit.SECONDS);
        }
    }

    private static void lockTimeout() {
        JpaTestContext.entityManager().createNativeQuery("SET LOCAL lock_timeout = '300ms'").executeUpdate();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
