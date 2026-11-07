package com.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.post.repository.master.PostCounterQueries;
import com.community.testsupport.BoardFixture;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MemberFixture;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글·댓글 숫자 칸과 추천·조회 기록({@link PostCounterQueries}) — 원자적 대량 UPDATE 뒤 같은 트랜잭션에서 읽은 새 값이 예전 {@code UPDATE … RETURNING} 과 같고,
 * 동시에 올려도 값이 겹치지 않는다(행 잠금이 커밋까지 남는다). 기록 넣기({@code ON CONFLICT})는 처음만 true. 실제 PostgreSQL.
 */
class PostCounterQueriesTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(943);

    private static PostCounterQueries counters;
    private static TransactionTemplate tx;
    private static JdbcTemplate jdbc;
    private static MemberFixture members;
    private static BoardFixture boards;
    private static long boardId;

    @BeforeAll
    static void setUp() {
        counters = JpaTestContext.create(PostCounterQueries.class);
        tx = new TransactionTemplate(JpaTestContext.transactions());
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        members = new MemberFixture(JpaTestContext.context());
        boards = new BoardFixture(JpaTestContext.context());
        boardId = IDS.nextId();
        boards.board(boardId, "counter-" + boardId, "숫자 칸 시험", 991);
    }

    @AfterAll
    static void tearDown() {
        jdbc.update("DELETE FROM comment_likes WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", boardId);
        jdbc.update("DELETE FROM comment_dislikes WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", boardId);
        jdbc.update("DELETE FROM post_likes WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", boardId);
        jdbc.update("DELETE FROM post_views WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", boardId);
        jdbc.update("DELETE FROM comments WHERE board_id = ?", boardId);
        jdbc.update("DELETE FROM posts WHERE board_id = ?", boardId);
        boards.deleteBoard(boardId);
    }

    private static long member() {
        long id = IDS.nextId();
        members.user(id, "counter-" + id + "@test.local");
        return id;
    }

    private static long post(long author) {
        long id = IDS.nextId();
        boards.post(id, boardId, author, "숫자 칸", "본문");
        return id;
    }

    @Test
    void 글_추천은_처음만_넣고_추천_수를_새_값으로_돌려준다() {
        long author = member();
        long fan = member();
        long postId = post(author);
        assertThat((Boolean) tx.execute(s -> counters.insertPostLike(postId, fan))).isTrue();
        assertThat((Boolean) tx.execute(s -> counters.insertPostLike(postId, fan))).isFalse();
        assertThat(counters.postLiked(postId, fan)).isTrue();
        assertThat(counters.postLiked(postId, author)).isFalse();
        assertThat((Integer) tx.execute(s -> counters.incrementPostLikes(postId))).isEqualTo(1);
        assertThat((Integer) tx.execute(s -> counters.incrementPostLikes(postId))).isEqualTo(2);
        assertThat((Integer) tx.execute(s -> counters.incrementPostLikes(IDS.nextId()))).as("없는 글").isZero();
    }

    @Test
    void 조회는_중복_방지_시간이_지났을_때만_다시_센다() {
        long postId = post(member());
        String viewer = "m:" + IDS.nextId();
        Instant now = Instant.now();
        assertThat((Boolean) tx.execute(s -> counters.recordView(postId, viewer, now.minusSeconds(3600)))).as("처음").isTrue();
        assertThat((Boolean) tx.execute(s -> counters.recordView(postId, viewer, now.minusSeconds(3600)))).as("방금 셌다").isFalse();
        assertThat((Boolean) tx.execute(s -> counters.recordView(postId, viewer, now.plusSeconds(60)))).as("기록이 기준보다 앞").isTrue();
        assertThat((Integer) tx.execute(s -> counters.incrementViews(postId))).isEqualTo(1);
        assertThat((Integer) tx.execute(s -> counters.addCommentCount(postId, 3))).isEqualTo(3);
        assertThat((Integer) tx.execute(s -> counters.addCommentCount(postId, -5))).as("0 아래로 내려가지 않는다").isZero();
    }

    @Test
    void 댓글_추천_비추천은_둘_중_하나이고_수는_0_아래로_내려가지_않는다() {
        long author = member();
        long fan = member();
        long postId = post(author);
        long commentId = IDS.nextId();
        boards.comment(commentId, postId, boardId, author, "댓글");

        tx.executeWithoutResult(s -> {
            assertThat(counters.insertCommentLike(commentId, postId, fan)).isTrue();
            assertThat(counters.insertCommentLike(commentId, postId, fan)).isFalse();
            assertThat(counters.incrementCommentLikes(postId, commentId)).isEqualTo(1);
        });
        assertThat(counters.commentLiked(commentId, fan)).isTrue();
        assertThat(counters.likedComments(postId, fan)).containsExactly(commentId);

        tx.executeWithoutResult(s -> {
            assertThat(counters.insertCommentDislike(commentId, postId, fan)).isTrue();
            assertThat(counters.addCommentDislikes(postId, commentId, 1)).isEqualTo(1);
            assertThat(counters.deleteCommentLike(commentId, fan)).isTrue();
            assertThat(counters.deleteCommentLike(commentId, fan)).isFalse();
            assertThat(counters.decrementCommentLikes(postId, commentId)).isZero();
            assertThat(counters.decrementCommentLikes(postId, commentId)).as("0 아래로 내려가지 않는다").isZero();
        });
        assertThat(counters.commentDisliked(commentId, fan)).isTrue();
        assertThat(counters.dislikedComments(postId, fan)).containsExactly(commentId);
        assertThat(counters.commentReactionCounts(postId, commentId)).containsExactly(0, 1);

        tx.executeWithoutResult(s -> {
            assertThat(counters.deleteCommentDislike(commentId, fan)).isTrue();
            assertThat(counters.addCommentDislikes(postId, commentId, -1)).isZero();
            assertThat(counters.addCommentDislikes(postId, commentId, -1)).isZero();
        });
        assertThat(counters.commentReactionCounts(postId, commentId)).containsExactly(0, 0);
        assertThat(counters.likedComments(postId, fan)).isEmpty();
    }

    @Test
    void 동시에_올려도_돌려준_새_값이_겹치지_않는다() throws Exception {
        long postId = post(member());
        int workers = 8;
        List<CompletableFuture<Integer>> all = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            all.add(CompletableFuture.supplyAsync(() -> tx.execute(s -> counters.incrementViews(postId))));
        }
        List<Integer> got = new ArrayList<>();
        for (CompletableFuture<Integer> f : all) {
            got.add(f.get());
        }
        assertThat(got).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId)).isEqualTo(workers);
    }

    @Test
    void 작성자_줄_세우기는_트랜잭션_안에서_잡힌다() {
        tx.executeWithoutResult(s -> counters.lockWriter("post:m:" + IDS.nextId()));
    }
}
