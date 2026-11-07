package com.community.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.repository.master.BoardContentQueries;
import com.community.testsupport.BoardFixture;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MemberFixture;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시판을 지울 때의 글 정리({@link BoardContentQueries#deleteContent}) — 추천·조회 기록 네 표의 삭제가 엔티티 대량 DELETE(부분 질의)로 바뀐 뒤에도 그 게시판 글의 줄만 지운다.
 * 실제 PostgreSQL.
 */
class BoardContentQueriesTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(945);

    @Test
    void 게시판을_지우면_그_게시판_글의_추천_조회_기록만_함께_지운다() {
        BoardContentQueries queries = JpaTestContext.create(BoardContentQueries.class);
        TransactionTemplate tx = new TransactionTemplate(JpaTestContext.transactions());
        JdbcTemplate jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        BoardFixture boards = new BoardFixture(JpaTestContext.context());
        MemberFixture members = new MemberFixture(JpaTestContext.context());

        long member = IDS.nextId();
        members.user(member, "board-content-" + member + "@test.local");
        long gone = IDS.nextId();
        long kept = IDS.nextId();
        boards.board(gone, "bc-gone-" + gone, "지울 게시판", 992);
        boards.board(kept, "bc-kept-" + kept, "남을 게시판", 993);
        long gonePost = IDS.nextId();
        long keptPost = IDS.nextId();
        boards.post(gonePost, gone, member, "지울 글", "본문");
        boards.post(keptPost, kept, member, "남을 글", "본문");
        long goneComment = IDS.nextId();
        long keptComment = IDS.nextId();
        boards.comment(goneComment, gonePost, gone, member, "댓글");
        boards.comment(keptComment, keptPost, kept, member, "댓글");
        for (long[] p : new long[][] {{gonePost, goneComment}, {keptPost, keptComment}}) {
            jdbc.update("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)", p[0], member);
            jdbc.update("INSERT INTO post_views (post_id, viewer_key) VALUES (?, ?)", p[0], "m:" + member);
            jdbc.update("INSERT INTO comment_likes (comment_id, user_id, post_id) VALUES (?, ?, ?)", p[1], member, p[0]);
            jdbc.update("INSERT INTO comment_dislikes (comment_id, user_id, post_id) VALUES (?, ?, ?)", p[1], member, p[0]);
        }
        try {
            tx.executeWithoutResult(s -> queries.deleteContent(gone));

            for (String table : new String[] {"post_likes", "post_views", "comment_likes", "comment_dislikes"}) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE post_id = ?", Integer.class, gonePost)).as(table + " 지운 게시판").isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE post_id = ?", Integer.class, keptPost)).as(table + " 다른 게시판").isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM posts WHERE board_id = ?", Integer.class, gone)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE board_id = ?", Integer.class, gone)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM posts WHERE board_id = ?", Integer.class, kept)).isEqualTo(1);
        } finally {
            tx.executeWithoutResult(s -> queries.deleteContent(kept));
            boards.deleteBoard(gone);
            boards.deleteBoard(kept);
        }
    }
}
