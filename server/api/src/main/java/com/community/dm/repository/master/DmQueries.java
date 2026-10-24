package com.community.dm.repository.master;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 쪽지 표(V70 {@code dm_threads}·{@code dm_boxes}·{@code dm_messages}·{@code dm_user_states}) 읽기·쓰기 — 쪽지 서비스만 부른다. 트랜잭션은 부르는 서비스가 연다(같은 연결에 참여한다).
 *
 * <p>Native SQL(JDBC)인 이유: 대화 짝 만들기의 동시성({@code INSERT … ON CONFLICT DO NOTHING} — 두 사람이 동시에 첫 쪽지를 보내도 대화는 하나), 회원 한도 줄의 잠금·upsert,
 * {@code greatest()} 로 가르는 안 읽음 판정과 쪽지함·안 읽은 수의 묶음 집계를 한 문장씩으로 한다. 쪽지는 JPA 엔티티를 두지 않는다(이 클래스가 모든 읽기·쓰기를 한다 —
 * 영속성 컨텍스트와 섞여 flush 차례가 꼬이지 않게).
 */
@Repository
public class DmQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public DmQueries(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    // ── 회원 한 줄(수신 설정·오늘 한도) ──────────────────────────────────────

    /**
     * @param quotaDay 한도를 센 날(한국 시간) — 오늘이 아니면 센 수는 0 으로 본다
     */
    public record State(boolean acceptDm, LocalDate quotaDay, int sentToday, int newPartnersToday, Instant lastSentAt) {

        public static State initial() {
            return new State(true, null, 0, 0, null);
        }

        public int sentOn(LocalDate day) {
            return day.equals(quotaDay) ? sentToday : 0;
        }

        public int newPartnersOn(LocalDate day) {
            return day.equals(quotaDay) ? newPartnersToday : 0;
        }
    }

    /** 줄이 없으면 처음 값(받기·0통) */
    public State state(long userId) {
        List<State> rows = jdbc.query("select accept_dm, quota_day, sent_today, new_partners_today, last_sent_at from dm_user_states where user_id = :u",
                Map.of("u", userId), (rs, i) -> state(rs));
        return rows.isEmpty() ? State.initial() : rows.getFirst();
    }

    /** 보내기 전에 내 줄을 잠근다(없으면 만든 뒤) — 간격·한도 판정과 차감이 겹치지 않는다(서버가 여러 대여도) */
    public State lockState(long userId, Instant now) {
        jdbc.update("insert into dm_user_states (user_id, updated_at) values (:u, :now) on conflict (user_id) do nothing",
                new MapSqlParameterSource("u", userId).addValue("now", Timestamp.from(now)));
        return jdbc.queryForObject("select accept_dm, quota_day, sent_today, new_partners_today, last_sent_at from dm_user_states where user_id = :u for update",
                Map.of("u", userId), (rs, i) -> state(rs));
    }

    /** 이 회원들의 수신 설정 — 줄이 없으면 받기 */
    public boolean acceptsDm(long userId) {
        return state(userId).acceptDm();
    }

    public void setAccept(long userId, boolean accept, Instant now) {
        jdbc.update("insert into dm_user_states (user_id, accept_dm, updated_at) values (:u, :a, :now) "
                        + "on conflict (user_id) do update set accept_dm = excluded.accept_dm, updated_at = excluded.updated_at",
                new MapSqlParameterSource("u", userId).addValue("a", accept).addValue("now", Timestamp.from(now)));
    }

    /** 보낸 뒤의 오늘 사용량(잠근 줄에 쓴다) */
    public void recordSend(long userId, LocalDate day, int sentToday, int newPartnersToday, Instant now) {
        jdbc.update("update dm_user_states set quota_day = :d, sent_today = :s, new_partners_today = :n, last_sent_at = :now, updated_at = :now where user_id = :u",
                new MapSqlParameterSource("u", userId).addValue("d", java.sql.Date.valueOf(day)).addValue("s", sentToday).addValue("n", newPartnersToday)
                        .addValue("now", Timestamp.from(now)));
    }

    // ── 대화·쪽지함 ───────────────────────────────────────────────────────

    /**
     * 내 쪽지함의 대화 한 줄.
     *
     * @param partnerSent 상대가 이 대화에서 쪽지를 보낸 적이 있다(링크·연락처 허용)
     * @param lastMessageId 대화의 마지막 쪽지(없으면 0)
     */
    public record Box(long threadId, long userId, long partnerId, long lastMessageId, long readUpTo, long clearedUpTo, boolean partnerSent) {

        /** 내 쪽지함에 보이는 쪽지의 아래 경계(그 ID 까지는 안 보인다) */
        public long visibleAfter() {
            return clearedUpTo;
        }
    }

    private static final String BOX_SELECT = "select b.thread_id, b.user_id, b.partner_id, b.last_message_id, b.read_up_to, b.cleared_up_to, "
            + "case when t.user_low_id = b.user_id then t.high_sent else t.low_sent end as partner_sent "
            + "from dm_boxes b join dm_threads t on t.id = b.thread_id ";

    public Optional<Box> box(long userId, long threadId) {
        return jdbc.query(BOX_SELECT + "where b.user_id = :u and b.thread_id = :t", Map.of("u", userId, "t", threadId), (rs, i) -> box(rs)).stream().findFirst();
    }

    /** 두 사람 사이의 대화(내 쪽지함 줄) — 내가 지웠어도 있다 */
    public Optional<Box> boxWith(long userId, long partnerId) {
        return jdbc.query(BOX_SELECT + "where b.user_id = :u and b.partner_id = :p", Map.of("u", userId, "p", partnerId), (rs, i) -> box(rs)).stream().findFirst();
    }

    /**
     * 대화를 만든다(두 사람의 쪽지함 줄과 함께). 같은 짝의 대화가 이미 있으면(동시에 첫 쪽지를 보냈다) 아무것도 하지 않는다.
     *
     * @return 이번에 만들었으면 true
     */
    public boolean createThread(long threadId, long a, long b, Instant now) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        int made = jdbc.update("insert into dm_threads (id, user_low_id, user_high_id, created_at) values (:id, :low, :high, :now) "
                        + "on conflict (user_low_id, user_high_id) do nothing",
                new MapSqlParameterSource("id", threadId).addValue("low", low).addValue("high", high).addValue("now", Timestamp.from(now)));
        if (made == 0) {
            return false;
        }
        jdbc.update("insert into dm_boxes (thread_id, user_id, partner_id) values (:t, :low, :high), (:t, :high, :low)",
                new MapSqlParameterSource("t", threadId).addValue("low", low).addValue("high", high));
        return true;
    }

    /** 대화 행을 잠근다(같은 대화의 동시 전송·보관 정리가 줄 선다) */
    public void lockThread(long threadId) {
        jdbc.queryForList("select id from dm_threads where id = :t for update", Map.of("t", threadId), Long.class);
    }

    public void insertMessage(long id, long threadId, long senderId, String body, Instant now) {
        jdbc.update("insert into dm_messages (id, thread_id, sender_id, body, created_at) values (:id, :t, :s, :b, :now)",
                new MapSqlParameterSource("id", id).addValue("t", threadId).addValue("s", senderId).addValue("b", body).addValue("now", Timestamp.from(now)));
    }

    /** 보낸 뒤 — 대화의 마지막 쪽지·보낸 쪽 표시, 두 쪽지함의 마지막 쪽지, 보낸 사람의 읽음 위치(보낸 쪽지까지) */
    public void afterSend(long threadId, long senderId, long messageId, Instant now) {
        jdbc.update("update dm_threads set last_message_id = :m, last_message_at = :now, "
                        + "low_sent = low_sent or user_low_id = :s, high_sent = high_sent or user_high_id = :s where id = :t",
                new MapSqlParameterSource("t", threadId).addValue("m", messageId).addValue("now", Timestamp.from(now)).addValue("s", senderId));
        jdbc.update("update dm_boxes set last_message_id = :m, read_up_to = case when user_id = :s then greatest(read_up_to, :m) else read_up_to end "
                + "where thread_id = :t", new MapSqlParameterSource("t", threadId).addValue("m", messageId).addValue("s", senderId));
    }

    /** 쪽지함 한 줄(마지막 쪽지 포함) */
    public record InboxRow(Box box, long lastSenderId, String lastBody, Instant lastAt) {
    }

    /** 내 쪽지함 — 보이는 쪽지가 있는 대화만, 마지막 쪽지가 최근인 것이 위. {@code beforeLastId} 는 앞 쪽의 마지막 줄의 마지막 쪽지 ID */
    public List<InboxRow> inbox(long userId, Long beforeLastId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId).addValue("lim", limit).addValue("before", beforeLastId);
        return jdbc.query("select b.thread_id, b.user_id, b.partner_id, b.last_message_id, b.read_up_to, b.cleared_up_to, "
                + "case when t.user_low_id = b.user_id then t.high_sent else t.low_sent end as partner_sent, m.sender_id, m.body, m.created_at "
                + "from dm_boxes b join dm_threads t on t.id = b.thread_id join dm_messages m on m.id = b.last_message_id "
                + "where b.user_id = :u and b.last_message_id > b.cleared_up_to" + (beforeLastId == null ? "" : " and b.last_message_id < :before")
                + " order by b.last_message_id desc limit :lim", p, (rs, i) -> new InboxRow(box(rs), rs.getLong("sender_id"), rs.getString("body"),
                rs.getTimestamp("created_at").toInstant()));
    }

    /** 대화마다 상대가 보낸 안 읽은 쪽지 수(내가 지운 이전 쪽지는 세지 않는다) */
    public Map<Long, Integer> unreadCounts(long userId, Collection<Long> threadIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (threadIds.isEmpty()) {
            return out;
        }
        jdbc.query("select b.thread_id, count(m.id) as n from dm_boxes b join dm_messages m on m.thread_id = b.thread_id "
                        + "and m.id > greatest(b.read_up_to, b.cleared_up_to) and m.sender_id <> b.user_id "
                        + "where b.user_id = :u and b.thread_id in (:ts) group by b.thread_id",
                new MapSqlParameterSource("u", userId).addValue("ts", threadIds), rs -> {
                    out.put(rs.getLong("thread_id"), rs.getInt("n"));
                });
        return out;
    }

    /** 안 읽은 쪽지가 있는 대화 수 — {@code cap} 까지만 센다(색인 {@code ix_dm_boxes_inbox} 안에서) */
    public int unreadThreads(long userId, int cap) {
        Integer n = jdbc.queryForObject("select count(*) from (select 1 from dm_boxes where user_id = :u "
                + "and last_message_id > greatest(read_up_to, cleared_up_to) limit :cap) x", new MapSqlParameterSource("u", userId).addValue("cap", cap), Integer.class);
        return n == null ? 0 : n;
    }

    /** 쪽지 한 통 */
    public record MessageRow(long id, long senderId, String body, Instant createdAt) {
    }

    /** 대화방 — {@code afterId}(내가 지운 지점) 뒤의 쪽지, 최근이 앞. {@code beforeId} 는 커서(그보다 오래된 쪽) */
    public List<MessageRow> messages(long threadId, long afterId, Long beforeId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("t", threadId).addValue("after", afterId).addValue("before", beforeId).addValue("lim", limit);
        return jdbc.query("select id, sender_id, body, created_at from dm_messages where thread_id = :t and id > :after"
                        + (beforeId == null ? "" : " and id < :before") + " order by id desc limit :lim", p,
                (rs, i) -> new MessageRow(rs.getLong("id"), rs.getLong("sender_id"), rs.getString("body"), rs.getTimestamp("created_at").toInstant()));
    }

    /** 지금까지 온 것을 읽음으로(내려가지 않는다) */
    public void markRead(long userId, long threadId) {
        jdbc.update("update dm_boxes set read_up_to = greatest(read_up_to, last_message_id) where user_id = :u and thread_id = :t",
                Map.of("u", userId, "t", threadId));
    }

    /** 내 쪽지함에서 지운다 — 지금 마지막 쪽지까지 안 보이게, 읽음도 그 자리까지 */
    public void clear(long userId, long threadId) {
        jdbc.update("update dm_boxes set cleared_up_to = greatest(cleared_up_to, last_message_id), read_up_to = greatest(read_up_to, last_message_id) "
                + "where user_id = :u and thread_id = :t", Map.of("u", userId, "t", threadId));
    }

    /** 양쪽 쪽지함 모두에서 지운 대화면 원문을 지운다(대화·쪽지함 줄은 남는다 — 새 상대 판정·링크 허용이 그대로) */
    public int purgeIfBothCleared(long threadId) {
        return jdbc.update("delete from dm_messages where thread_id = :t and not exists ("
                + "select 1 from dm_boxes b where b.thread_id = :t and b.cleared_up_to < b.last_message_id)", Map.of("t", threadId));
    }

    // ── 사람 ───────────────────────────────────────────────────────────────

    /**
     * 상대 회원의 지금 값 — {@code gone} 이면 탈퇴·삭제(표시는 「탈퇴한 회원」).
     */
    public record Person(long id, String nickname, String gradeKey, boolean gone) {
    }

    public Map<Long, Person> people(Collection<Long> ids) {
        Map<Long, Person> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("select u.id, p.nickname, p.grade_key, (u.status = 'WITHDRAWN' or u.deleted_at is not null or p.user_id is null) as gone "
                + "from users u left join profiles p on p.user_id = u.id where u.id in (:ids)", new MapSqlParameterSource("ids", ids), rs -> {
                    long id = rs.getLong("id");
                    out.put(id, new Person(id, rs.getString("nickname"), rs.getString("grade_key"), rs.getBoolean("gone")));
                });
        return out;
    }

    /** 받는 사람 찾기 한 줄 — {@code threadId} 는 두 사람 사이의 대화(없으면 null) */
    public record RecipientRow(long id, String nickname, String gradeKey, Long threadId) {
    }

    /**
     * 닉네임에 검색어가 들어 있는 회원 — 앞부분이 같은 것 먼저, 그다음 닉네임 순. 나·탈퇴한 회원·차단 관계(어느 쪽이든)는 뺀다.
     *
     * @param needle 소문자로 바꾼 검색어(LIKE 의 %·_·\ 는 부르는 쪽이 이스케이프하지 않는다 — 여기서 한다)
     */
    public List<RecipientRow> recipients(long userId, String needle, int limit) {
        String escaped = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId).addValue("contains", "%" + escaped + "%").addValue("prefix", escaped + "%")
                .addValue("lim", limit);
        return jdbc.query("select p.user_id, p.nickname, p.grade_key, b.thread_id from profiles p join users u on u.id = p.user_id "
                + "left join dm_boxes b on b.user_id = :u and b.partner_id = p.user_id "
                + "where u.status <> 'WITHDRAWN' and u.deleted_at is null and p.user_id <> :u and lower(p.nickname) like :contains escape '\\' "
                + "and not exists (select 1 from user_blocks k where (k.user_id = :u and k.target_id = p.user_id) or (k.user_id = p.user_id and k.target_id = :u)) "
                + "order by (lower(p.nickname) like :prefix escape '\\') desc, lower(p.nickname), p.user_id limit :lim", p, (rs, i) -> {
                    long thread = rs.getLong("thread_id");
                    return new RecipientRow(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("grade_key"), rs.wasNull() ? null : thread);
                });
    }

    /** 이 대화의 내 쪽지 알림(회원 알림 {@code DM}, 덧붙임 {@code threadId})을 읽음으로 — 대화를 읽음·지움·답장할 때 */
    public int markDmNotificationsRead(long userId, long threadId, Instant now) {
        return jdbc.update("update member_notifications set read_at = :now where user_id = :u and type = 'DM' and read_at is null and payload ->> 'threadId' = :t",
                new MapSqlParameterSource("u", userId).addValue("t", Long.toString(threadId)).addValue("now", Timestamp.from(now)));
    }

    // ── 매핑 ───────────────────────────────────────────────────────────────

    private static State state(ResultSet rs) throws SQLException {
        java.sql.Date day = rs.getDate("quota_day");
        Timestamp last = rs.getTimestamp("last_sent_at");
        return new State(rs.getBoolean("accept_dm"), day == null ? null : day.toLocalDate(), rs.getInt("sent_today"), rs.getInt("new_partners_today"),
                last == null ? null : last.toInstant());
    }

    private static Box box(ResultSet rs) throws SQLException {
        return new Box(rs.getLong("thread_id"), rs.getLong("user_id"), rs.getLong("partner_id"), rs.getLong("last_message_id"), rs.getLong("read_up_to"),
                rs.getLong("cleared_up_to"), rs.getBoolean("partner_sent"));
    }
}
