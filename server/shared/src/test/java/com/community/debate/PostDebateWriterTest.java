package com.community.debate;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.debate.entity.PostDebateEntity;
import com.community.debate.repository.master.DebateQueries;
import com.community.debate.repository.master.PostDebateRepository;
import com.community.debate.service.PostDebateWriter;
import com.community.debate.service.PostDebateWriter.Change;
import com.community.debate.service.PostDebateWriter.State;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 토론 열기·바꾸기·닫기({@link PostDebateWriter}) — 열기의 {@code INSERT … ON CONFLICT DO NOTHING} 을 저장소({@link DebateQueries#openIfAbsent})로 옮긴 뒤에도 예전과 같은 결과다.
 * 실제 PostgreSQL. 글 ID 는 시험마다 새로 만든다({@code post_debates} 는 글 표를 참조하지 않는다).
 */
class PostDebateWriterTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(943);

    private static PostDebateWriter writer;
    private static TransactionTemplate tx;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        writer = new PostDebateWriter(JpaTestContext.context().getBean(PostDebateRepository.class), JpaTestContext.create(DebateQueries.class));
        tx = new TransactionTemplate(JpaTestContext.transactions());
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
    }

    @AfterAll
    static void tearDown() {
        jdbc.update("DELETE FROM post_debates WHERE opened_by = -943"); // 둘째 시험은 닫아서 이미 없다
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static Change apply(long postId, boolean enabled, Boolean neutral, Boolean change, String byKind, Long by) {
        return tx.execute(s -> writer.apply(postId, enabled, neutral, change, byKind, by, now()));
    }

    @Test
    void 열면_한_줄이_생기고_다시_열면_바뀐_칸만_고친다_연_사람은_그대로() {
        long postId = IDS.nextId();
        Change opened = apply(postId, true, null, null, PostDebateEntity.BY_MEMBER, -943L);
        assertThat(opened.opened()).isTrue();
        assertThat(opened.after()).isEqualTo(new State(true, true, PostDebateEntity.BY_MEMBER));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM post_debates WHERE post_id = ?", postId);
        assertThat(row.get("opened_by")).isEqualTo(-943L);
        assertThat(row.get("updated_by_kind")).isEqualTo(PostDebateEntity.BY_MEMBER);
        assertThat(row.get("created_at")).isEqualTo(row.get("updated_at"));

        Change changed = apply(postId, true, false, null, PostDebateEntity.BY_OPERATOR, -943L);
        assertThat(changed.updated()).isTrue();
        assertThat(changed.before()).isEqualTo(new State(true, true, PostDebateEntity.BY_MEMBER));
        assertThat(changed.after()).isEqualTo(new State(false, true, PostDebateEntity.BY_MEMBER));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_debates WHERE post_id = ?", Integer.class, postId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT updated_by_kind FROM post_debates WHERE post_id = ?", String.class, postId)).isEqualTo(PostDebateEntity.BY_OPERATOR);

        Change same = apply(postId, true, false, true, PostDebateEntity.BY_OPERATOR, -943L);
        assertThat(same.updated()).isFalse();
        assertThat(same.opened()).isFalse();
    }

    @Test
    void 연_사람이_없어도_열리고_닫으면_행이_없어진다() {
        long postId = IDS.nextId();
        Change opened = apply(postId, true, false, false, PostDebateEntity.BY_OPERATOR, null);
        assertThat(opened.after()).isEqualTo(new State(false, false, PostDebateEntity.BY_OPERATOR));
        assertThat(jdbc.queryForObject("SELECT opened_by FROM post_debates WHERE post_id = ?", Long.class, postId)).isNull();

        Change closed = apply(postId, false, null, null, PostDebateEntity.BY_OPERATOR, null);
        assertThat(closed.closed()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_debates WHERE post_id = ?", Integer.class, postId)).isZero();
        assertThat(apply(postId, false, null, null, PostDebateEntity.BY_OPERATOR, null)).isEqualTo(new Change(null, null));
    }
}
