package com.community.dm;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.dm.repository.master.DmQueries;
import com.community.dm.repository.master.DmQueries.Person;
import com.community.dm.repository.master.DmQueries.RecipientRow;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 쪽지의 사람 읽기(회원·프로필·차단 — QueryDSL)와 받는 사람 찾기의 대화 번호(쪽지함 — SQL)가 예전 한 문장과 같은 결과를 낸다. 실제 PostgreSQL.
 * DB 를 다른 시험과 같이 쓰므로 닉네임에 시험마다 새 글자를 넣고, 만든 회원은 끝나면 지운다(쪽지함·차단은 연쇄 삭제).
 */
class DmQueriesPeopleTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(923);

    private static JdbcTemplate jdbc;
    private static DmQueries dm;
    private final List<Long> users = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        dm = new DmQueries(JpaTestContext.query(), JpaTestContext.dataSource());
    }

    @AfterEach
    void cleanUp() {
        for (long id : users) {
            jdbc.update("DELETE FROM dm_threads WHERE user_low_id = ? OR user_high_id = ?", id, id);
            jdbc.update("DELETE FROM profiles WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private long member(String status, String nickname, boolean deleted) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO users (id, email, status, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at, deleted_at) "
                + "VALUES (?, ?, ?, now(), 'v1', now(), now(), " + (deleted ? "now()" : "NULL") + ")", id, "dm" + id + "@test.local", status);
        if (nickname != null) {
            jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, nickname);
        }
        users.add(id);
        return id;
    }

    @Test
    void 사람_읽기는_탈퇴_삭제_프로필_없음을_gone_탈퇴_진행_중을_withdrawing_으로() {
        String t = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36, 36L * 36 * 36 * 36 * 36 * 36), 36);
        long active = member("ACTIVE", "pa" + t, false);
        long withdrawn = member("WITHDRAWN", "pw" + t, false);
        long deleted = member("ACTIVE", "pd" + t, true);
        long noProfile = member("ACTIVE", null, false);
        long withdrawing = member("WITHDRAWING", "pi" + t, false);

        Map<Long, Person> people = dm.people(List.of(active, withdrawn, deleted, noProfile, withdrawing, IDS.nextId()));

        assertThat(people).hasSize(5);
        assertThat(people.get(active).nickname()).isEqualTo("pa" + t);
        assertThat(people.get(active).gone()).isFalse();
        assertThat(people.get(active).withdrawing()).isFalse();
        assertThat(people.get(withdrawn).gone()).isTrue();
        assertThat(people.get(deleted).gone()).isTrue();
        assertThat(people.get(noProfile).gone()).isTrue();
        assertThat(people.get(noProfile).nickname()).isNull();
        assertThat(people.get(withdrawing).gone()).isFalse();
        assertThat(people.get(withdrawing).withdrawing()).isTrue();
    }

    @Test
    void 받는_사람_찾기는_앞부분이_같은_것_먼저_나_탈퇴_차단을_빼고_대화_번호를_붙인다() {
        String t = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36, 36L * 36 * 36 * 36 * 36 * 36), 36);
        long me = member("ACTIVE", t + "me", false);
        long prefixB = member("ACTIVE", t + "b", false);
        long prefixA = member("ACTIVE", t + "a", false);
        long contains = member("ACTIVE", "x" + t, false);
        long withdrawing = member("WITHDRAWING", t + "w", false);
        long blockedByMe = member("ACTIVE", t + "k", false);
        long blocksMe = member("ACTIVE", t + "j", false);
        long underscore = member("ACTIVE", t + "_u", false);
        jdbc.update("INSERT INTO user_blocks (id, user_id, target_id, created_at) VALUES (?, ?, ?, now())", IDS.nextId(), me, blockedByMe);
        jdbc.update("INSERT INTO user_blocks (id, user_id, target_id, created_at) VALUES (?, ?, ?, now())", IDS.nextId(), blocksMe, me);
        long thread = IDS.nextId();
        dm.createThread(thread, me, prefixB, Instant.now());

        List<RecipientRow> rows = dm.recipients(me, t, 20);

        assertThat(rows).extracting(RecipientRow::id).containsExactlyInAnyOrder(underscore, prefixA, prefixB, contains);
        assertThat(rows.getLast().id()).as("앞부분이 같은 것 먼저").isEqualTo(contains);
        assertThat(rows).extracting(RecipientRow::id).filteredOn(id -> id != underscore).containsExactly(prefixA, prefixB, contains);
        assertThat(rows).filteredOn(r -> r.id() == prefixB).extracting(RecipientRow::threadId).containsExactly(thread);
        assertThat(rows).filteredOn(r -> r.id() == prefixA).extracting(RecipientRow::threadId).containsOnlyNulls();
        assertThat(dm.recipients(me, t + "_", 20)).as("LIKE 의 _ 는 글자 그대로").extracting(RecipientRow::id).containsExactly(underscore);
        assertThat(dm.recipients(me, t, 3)).as("개수는 DB 에서").hasSize(3).extracting(RecipientRow::id).doesNotContain(contains);
    }
}
