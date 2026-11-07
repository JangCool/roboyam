package com.community.dm;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.dm.repository.master.DmBoxRepository;
import com.community.dm.repository.master.DmMessageRepository;
import com.community.dm.repository.master.DmQueries;
import com.community.dm.repository.master.DmQueries.Box;
import com.community.dm.repository.master.DmQueries.InboxRow;
import com.community.dm.repository.master.DmQueries.MessageRow;
import com.community.dm.repository.master.DmQueries.State;
import com.community.membernotification.repository.master.MemberNotificationPreferenceQueries;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 쪽지 표(dm_*)·회원 알림 선택(member_notification_preferences)의 JPA·QueryDSL 전환(2단계)이 예전 SQL 과 같은 결과를 낸다 — 대화 만들기(경합 진 쪽)·보내기·쪽지함·안 읽은 수·읽음·지움·원문 정리·
 * 쪽지 알림 읽음·상태 줄 잠금·수신 설정·알림 선택 upsert. 실제 PostgreSQL, 운영과 같은 JPA 트랜잭션({@link JpaTestContext}). DB 를 다른 시험과 같이 쓰므로 만든 회원은 끝나면 지운다(연쇄 삭제).
 */
class DmQueriesWriteTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(924);

    private static JdbcTemplate jdbc;
    private static DmQueries dm;
    private static MemberNotificationPreferenceQueries preferences;
    private static TransactionTemplate tx;
    private final List<Long> users = new ArrayList<>();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeAll
    static void setUp() {
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        dm = new DmQueries(JpaTestContext.query(), JpaTestContext.context().getBean(DmBoxRepository.class), JpaTestContext.context().getBean(DmMessageRepository.class),
                JpaTestContext.dataSource());
        preferences = new MemberNotificationPreferenceQueries(JpaTestContext.query(), JpaTestContext.dataSource());
        tx = new TransactionTemplate(JpaTestContext.transactions());
    }

    @AfterEach
    void cleanUp() {
        for (long id : users) {
            jdbc.update("DELETE FROM dm_threads WHERE user_low_id = ? OR user_high_id = ?", id, id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private long member() {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO users (id, email, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, now(), 'v1', now(), now())", id,
                "dmw" + id + "@test.local");
        users.add(id);
        return id;
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(s -> work.get());
    }

    private void inTx(Runnable work) {
        tx.executeWithoutResult(s -> work.run());
    }

    /** {@code DmService#send} 의 쓰기 차례 그대로 — 대화 잠금 → 쪽지 → 보낸 뒤 */
    private long send(long threadId, long from, String body, Instant at) {
        long messageId = IDS.nextId();
        inTx(() -> {
            dm.lockThread(threadId);
            dm.insertMessage(messageId, threadId, from, body, at);
            dm.afterSend(threadId, from, messageId, at);
        });
        return messageId;
    }

    @Test
    void 대화는_짝마다_하나고_보내기가_대화와_두_쪽지함을_고친다() {
        long a = member();
        long b = member();
        long thread = IDS.nextId();
        assertThat(inTx(() -> dm.createThread(thread, b, a, now))).isTrue();
        assertThat(inTx(() -> dm.createThread(IDS.nextId(), a, b, now))).as("같은 짝의 두 번째 대화(경합에서 진 쪽)").isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dm_threads WHERE user_low_id = ? AND user_high_id = ?", Integer.class, Math.min(a, b), Math.max(a, b)))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT user_id || ':' || partner_id || ':' || last_message_id || ':' || read_up_to || ':' || cleared_up_to FROM dm_boxes "
                + "WHERE thread_id = ? ORDER BY user_id", String.class, thread))
                .containsExactly(Math.min(a, b) + ":" + Math.max(a, b) + ":0:0:0", Math.max(a, b) + ":" + Math.min(a, b) + ":0:0:0");
        assertThat(dm.boxWith(a, b)).contains(new Box(thread, a, b, 0, 0, 0, false));

        long m1 = send(thread, a, "안녕", now);
        // a 가 보냈다 — a 쪽 표시만 켜지고, 두 쪽지함의 마지막 쪽지가 m1, 보낸 사람만 읽음이 m1
        boolean aIsLow = a < b;
        assertThat(jdbc.queryForObject("SELECT low_sent FROM dm_threads WHERE id = ?", Boolean.class, thread)).isEqualTo(aIsLow);
        assertThat(jdbc.queryForObject("SELECT high_sent FROM dm_threads WHERE id = ?", Boolean.class, thread)).isEqualTo(!aIsLow);
        assertThat(jdbc.queryForObject("SELECT last_message_id FROM dm_threads WHERE id = ?", Long.class, thread)).isEqualTo(m1);
        assertThat(dm.box(a, thread)).contains(new Box(thread, a, b, m1, m1, 0, false));
        assertThat(dm.box(b, thread)).as("b 에게는 상대(a)가 보낸 대화").contains(new Box(thread, b, a, m1, 0, 0, true));

        long m2 = send(thread, b, "반가워", now.plusSeconds(5));
        assertThat(jdbc.queryForObject("SELECT low_sent AND high_sent FROM dm_threads WHERE id = ?", Boolean.class, thread)).isTrue();
        assertThat(dm.box(a, thread)).contains(new Box(thread, a, b, m2, m1, 0, true));
        assertThat(dm.box(b, thread)).contains(new Box(thread, b, a, m2, m2, 0, true));

        // 쪽지함·안 읽은 수(상대가 보낸 것만)
        List<InboxRow> inbox = dm.inbox(a, null, 10);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.getFirst().lastSenderId()).isEqualTo(b);
        assertThat(inbox.getFirst().lastBody()).isEqualTo("반가워");
        assertThat(inbox.getFirst().lastAt()).isEqualTo(now.plusSeconds(5));
        assertThat(dm.inbox(a, m2, 10)).as("커서 = 마지막 쪽지 ID 앞").isEmpty();
        assertThat(dm.unreadCounts(a, List.of(thread))).isEqualTo(Map.of(thread, 1));
        assertThat(dm.unreadCounts(b, List.of(thread))).as("마지막이 내 쪽지면 안 읽음이 없다").isEmpty();
        assertThat(dm.unreadThreads(a, 99)).isEqualTo(1);
        assertThat(dm.unreadThreads(b, 99)).isZero();

        // 대화방 — 최근이 앞, 커서
        assertThat(dm.messages(thread, 0, null, 10)).containsExactly(new MessageRow(m2, b, "반가워", now.plusSeconds(5)), new MessageRow(m1, a, "안녕", now));
        assertThat(dm.messages(thread, 0, m2, 10)).containsExactly(new MessageRow(m1, a, "안녕", now));
        assertThat(dm.messages(thread, m1, null, 10)).extracting(MessageRow::id).containsExactly(m2);

        // 읽음 — 내려가지 않는다
        inTx(() -> dm.markRead(a, thread));
        assertThat(dm.box(a, thread)).contains(new Box(thread, a, b, m2, m2, 0, true));
        assertThat(dm.unreadThreads(a, 99)).isZero();

        // 지움 — 한쪽만 지우면 원문은 남고, 둘 다 지우면 원문만 지운다(대화·쪽지함 줄은 남는다)
        inTx(() -> dm.clear(b, thread));
        assertThat(dm.box(b, thread)).contains(new Box(thread, b, a, m2, m2, m2, true));
        assertThat(dm.inbox(b, null, 10)).isEmpty();
        assertThat(inTx(() -> dm.purgeIfBothCleared(thread))).isZero();
        inTx(() -> dm.clear(a, thread));
        assertThat(inTx(() -> dm.purgeIfBothCleared(thread))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dm_messages WHERE thread_id = ?", Integer.class, thread)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dm_boxes WHERE thread_id = ?", Integer.class, thread)).isEqualTo(2);
    }

    @Test
    void 쪽지_알림_읽음은_그_대화의_안_읽은_DM_만() {
        long a = member();
        long b = member();
        long thread = IDS.nextId();
        long other = thread + 1;
        long hit = IDS.nextId();
        long otherThread = IDS.nextId();
        long otherType = IDS.nextId();
        String insert = "INSERT INTO member_notifications (id, user_id, type, actor_user_id, payload, created_at) VALUES (?, ?, ?, ?, ?::jsonb, now())";
        jdbc.update(insert, hit, a, "DM", b, "{\"threadId\": \"" + thread + "\"}");
        jdbc.update(insert, otherThread, a, "DM", b, "{\"threadId\": \"" + other + "\"}");
        jdbc.update(insert, otherType, a, "COMMENT", b, "{\"threadId\": \"" + thread + "\"}");

        assertThat(inTx(() -> dm.markDmNotificationsRead(a, thread, now))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT read_at FROM member_notifications WHERE id = ?", OffsetDateTime.class, hit).toInstant()).isEqualTo(now);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE id IN (?, ?) AND read_at IS NULL", Integer.class, otherThread, otherType))
                .isEqualTo(2);
        assertThat(inTx(() -> dm.markDmNotificationsRead(a, thread, now))).as("이미 읽은 것은 다시 세지 않는다").isZero();
    }

    @Test
    void 상태_줄은_잠글_때_만들고_보낸_뒤_사용량과_수신_설정을_쓴다() {
        long a = member();
        assertThat(dm.state(a)).isEqualTo(State.initial());
        assertThat(dm.acceptsDm(a)).isTrue();

        State locked = inTx(() -> dm.lockState(a, now));
        assertThat(locked).isEqualTo(State.initial());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dm_user_states WHERE user_id = ?", Integer.class, a)).isEqualTo(1);

        LocalDate day = LocalDate.of(2026, 11, 7);
        inTx(() -> {
            dm.lockState(a, now); // 이미 있으면 그대로 잠근다
            dm.recordSend(a, day, 3, 1, now);
        });
        assertThat(dm.state(a)).isEqualTo(new State(true, day, 3, 1, now));
        assertThat(inTx(() -> dm.lockState(a, now.plusSeconds(1)))).isEqualTo(new State(true, day, 3, 1, now));

        inTx(() -> dm.setAccept(a, false, now));
        assertThat(dm.acceptsDm(a)).isFalse();
        assertThat(dm.state(a)).as("수신 설정만 바뀐다").isEqualTo(new State(false, day, 3, 1, now));
        long fresh = member();
        inTx(() -> dm.setAccept(fresh, false, now));
        assertThat(dm.state(fresh)).isEqualTo(new State(false, null, 0, 0, null));
    }

    @Test
    void 알림_선택은_upsert_로_저장하고_줄이_있는_이벤트만_읽는다() {
        long a = member();
        assertThat(preferences.choicesOf(a)).isEmpty();
        inTx(() -> {
            preferences.upsert(a, "COMMENT", false, true, now);
            preferences.upsert(a, "DM", true, false, now);
        });
        inTx(() -> preferences.upsert(a, "COMMENT", true, false, now.plusSeconds(1)));
        assertThat(preferences.choicesOf(a)).isEqualTo(Map.of("COMMENT", new MemberNotificationPreferenceQueries.Choice(true, false), "DM",
                new MemberNotificationPreferenceQueries.Choice(true, false)));
        assertThat(jdbc.queryForObject("SELECT updated_at FROM member_notification_preferences WHERE user_id = ? AND event_code = 'COMMENT'", OffsetDateTime.class, a).toInstant())
                .isEqualTo(now.plusSeconds(1));
    }
}
