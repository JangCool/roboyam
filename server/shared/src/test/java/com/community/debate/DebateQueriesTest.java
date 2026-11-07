package com.community.debate;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.debate.dto.DebateVotesView;
import com.community.debate.entity.DebateStance;
import com.community.debate.repository.master.DebateQueries;
import com.community.testsupport.BoardFixture;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MemberFixture;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 토론 저장소({@link DebateQueries}) — 입장(투표)·입장 바꾸기의 의견 소급·의견 고정이 JPA·QueryDSL 전환 뒤에도 예전 SQL 과 같은 결과다. 실제 PostgreSQL.
 */
class DebateQueriesTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(942);

    private static DebateQueries debates;
    private static TransactionTemplate tx;
    private static JdbcTemplate jdbc;
    private static MemberFixture members;
    private static BoardFixture boards;
    private static long boardId;

    @BeforeAll
    static void setUp() {
        debates = JpaTestContext.create(DebateQueries.class);
        tx = new TransactionTemplate(JpaTestContext.transactions());
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        members = new MemberFixture(JpaTestContext.context());
        boards = new BoardFixture(JpaTestContext.context());
        boardId = IDS.nextId();
        boards.board(boardId, "debate-" + boardId, "토론 시험", 990);
    }

    @AfterAll
    static void tearDown() {
        jdbc.update("DELETE FROM comments WHERE board_id = ?", boardId);
        jdbc.update("DELETE FROM posts WHERE board_id = ?", boardId);
        boards.deleteBoard(boardId);
    }

    private static long member() {
        long id = IDS.nextId();
        members.user(id, "debate-" + id + "@test.local");
        return id;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void 처음_입장은_한_줄만_남고_바꾸기는_줄_수를_돌려준다() {
        long postId = IDS.nextId();
        long a = member();
        assertThat((Boolean) tx.execute(s -> debates.insertIfAbsent(postId, a, DebateStance.PRO, now()))).isTrue();
        assertThat((Boolean) tx.execute(s -> debates.insertIfAbsent(postId, a, DebateStance.CON, now()))).as("이미 있으면 그대로").isFalse();
        assertThat(debates.stanceOf(postId, a)).contains(DebateStance.PRO);
        assertThat(debates.stanceOf(postId, member())).isEmpty();

        tx.executeWithoutResult(s -> {
            assertThat(debates.lockStance(postId, a)).contains(DebateStance.PRO);
            assertThat(debates.change(postId, a, DebateStance.NEUTRAL, now())).isEqualTo(1);
            assertThat(debates.stanceOf(postId, a)).as("같은 트랜잭션에서 바뀐 값").contains(DebateStance.NEUTRAL);
            assertThat(debates.lockStance(postId, a)).contains(DebateStance.NEUTRAL);
        });
        assertThat((Integer) tx.execute(s -> debates.change(postId, member(), DebateStance.CON, now()))).isZero();
        assertThat((java.util.Optional<DebateStance>) tx.execute(s -> debates.lockStance(postId, IDS.nextId()))).isEmpty();
    }

    @Test
    void 투표_수는_글별_입장별로_센다() {
        long p1 = IDS.nextId();
        long p2 = IDS.nextId();
        long none = IDS.nextId();
        tx.executeWithoutResult(s -> {
            debates.insertIfAbsent(p1, member(), DebateStance.PRO, now());
            debates.insertIfAbsent(p1, member(), DebateStance.PRO, now());
            debates.insertIfAbsent(p1, member(), DebateStance.CON, now());
            debates.insertIfAbsent(p2, member(), DebateStance.NEUTRAL, now());
        });
        Map<Long, DebateVotesView> votes = debates.votesOf(List.of(p1, p2, none));
        assertThat(votes.get(p1)).isEqualTo(DebateVotesView.of(2, 1, 0));
        assertThat(votes.get(p2)).isEqualTo(DebateVotesView.of(0, 0, 1));
        assertThat(votes.get(none)).isEqualTo(DebateVotesView.EMPTY);
        assertThat(debates.votesOf(p1)).isEqualTo(DebateVotesView.of(2, 1, 0));
        assertThat(debates.votesOf(List.of())).isEmpty();
    }

    @Test
    void 의견_소급은_입장_있는_댓글만_바꾸고_지우지_않은_것만_센다() {
        long author = member();
        long postId = IDS.nextId();
        boards.post(postId, boardId, author, "토론 글", "본문");
        long pro = IDS.nextId();
        long proDeleted = IDS.nextId();
        long plain = IDS.nextId();
        long already = IDS.nextId();
        for (long id : new long[] {pro, proDeleted, plain, already}) {
            boards.comment(id, postId, boardId, author, "의견 " + id);
        }
        jdbc.update("UPDATE comments SET stance = 'PRO' WHERE id IN (?, ?)", pro, proDeleted);
        jdbc.update("UPDATE comments SET stance = 'CON' WHERE id = ?", already);
        jdbc.update("UPDATE comments SET deleted_at = now() WHERE id = ?", proDeleted);

        assertThat((Integer) tx.execute(s -> debates.moveOpinions(postId, author, DebateStance.CON))).as("지우지 않은 의견 하나(이미 CON 인 것·일반 댓글은 그대로)").isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT coalesce(stance, '-') FROM comments WHERE id IN (?, ?, ?, ?) ORDER BY id", String.class, pro, proDeleted, plain, already))
                .containsExactly("CON", "CON", "-", "CON");
        assertThat((Integer) tx.execute(s -> debates.moveOpinions(postId, author, DebateStance.CON))).isZero();
    }

    @Test
    void 고정은_멱등이고_고정한_차례로_읽는다() {
        long postId = IDS.nextId();
        long c1 = IDS.nextId();
        long c2 = IDS.nextId();
        Instant at = now();
        tx.executeWithoutResult(s -> {
            debates.pin(c2, postId, null, at);
            debates.pin(c1, postId, null, at.plusSeconds(1));
            debates.pin(c2, postId, null, at.plusSeconds(5)); // 이미 고정 — 처음 시각 그대로
        });
        assertThat(debates.pinnedInOrder(postId)).containsExactly(c2, c1);
        assertThat(debates.pinnedOf(postId)).containsExactlyInAnyOrder(c1, c2);
        assertThat(jdbc.queryForObject("SELECT pinned_by FROM debate_pins WHERE comment_id = ?", Long.class, c2)).isNull();
        tx.executeWithoutResult(s -> debates.unpin(c2));
        tx.executeWithoutResult(s -> debates.unpin(c2));
        assertThat(debates.pinnedInOrder(postId)).containsExactly(c1);
    }
}
