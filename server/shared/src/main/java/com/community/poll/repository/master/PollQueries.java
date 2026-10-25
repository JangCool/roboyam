package com.community.poll.repository.master;

import com.community.poll.dto.PollData;
import com.community.poll.dto.PollOptionData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 글 투표의 세 표(V81 {@code polls}·{@code poll_options}·{@code poll_votes}) — docs/features/post-poll-contract.md. <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>Native SQL 인 이유: 첫 표 잠금 규칙에 행 잠금 두 종류가 필요하다 — 투표를 고치거나 지우는 쪽은 {@code polls} 행을 {@code FOR UPDATE},
 * 투표하는 쪽은 {@code FOR SHARE}(투표끼리는 서로 막지 않고, 고치는 도중에 표가 들어오지 않는다). 한 사람의 동시 투표는 어드바이저리 잠금으로 줄을 세운다.
 * 세 표 모두 작은 줄이고 투표 하나가 한 덩어리로만 바뀌어 엔티티를 두지 않는다(같은 까닭의 {@code DebateQueries}).
 * <b>익명</b>: 누가 무엇을 골랐는지는 {@link #myOptionIds}(그 사람 본인 것)로만 읽는다 — 사람별 목록을 내는 질의는 두지 않는다.
 */
@Repository
public class PollQueries {

    private static final String POLL_COLUMNS = "SELECT id, post_id, question, multiple, change_allowed, closes_at, created_at FROM polls WHERE post_id = ?";

    private final JdbcTemplate jdbc;

    public PollQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 그 글의 투표(잠그지 않음) */
    public Optional<PollData> find(long postId) {
        return one(jdbc.query(POLL_COLUMNS, PollQueries::poll, postId));
    }

    /** 그 글의 투표 — 쓰기 잠금(고치기·지우기). 투표하는 쪽({@link #findForShare})과 서로 기다린다 */
    public Optional<PollData> findForUpdate(long postId) {
        return one(jdbc.query(POLL_COLUMNS + " FOR UPDATE", PollQueries::poll, postId));
    }

    /** 그 글의 투표 — 공유 잠금(투표하기). 투표끼리는 막지 않는다 */
    public Optional<PollData> findForShare(long postId) {
        return one(jdbc.query(POLL_COLUMNS + " FOR SHARE", PollQueries::poll, postId));
    }

    /** 선택지 — 차례대로 */
    public List<PollOptionData> options(long pollId) {
        return jdbc.query("SELECT id, position, label FROM poll_options WHERE poll_id = ? ORDER BY position",
                (rs, i) -> new PollOptionData(rs.getLong("id"), rs.getInt("position"), rs.getString("label")), pollId);
    }

    /** 참여한 사람 수(복수 선택이어도 한 사람은 한 번) */
    public int voterCount(long pollId) {
        Integer n = jdbc.queryForObject("SELECT count(DISTINCT user_id) FROM poll_votes WHERE poll_id = ?", Integer.class, pollId);
        return n == null ? 0 : n;
    }

    /** 표가 하나라도 있는가(첫 표 잠금) */
    public boolean hasVotes(long pollId) {
        return !jdbc.queryForList("SELECT 1 FROM poll_votes WHERE poll_id = ? LIMIT 1", Integer.class, pollId).isEmpty();
    }

    /** 선택지 ID → 득표 수(표가 없는 선택지는 빠진다) */
    public Map<Long, Integer> votesByOption(long pollId) {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query("SELECT option_id, count(*) AS n FROM poll_votes WHERE poll_id = ? GROUP BY option_id",
                rs -> {
                    out.put(rs.getLong("option_id"), rs.getInt("n"));
                }, pollId);
        return out;
    }

    /** 그 사람이 고른 선택지 — 선택지 차례대로(투표 전이면 빈 목록) */
    public List<Long> myOptionIds(long pollId, long userId) {
        return jdbc.queryForList("SELECT v.option_id FROM poll_votes v JOIN poll_options o ON o.id = v.option_id WHERE v.poll_id = ? AND v.user_id = ? ORDER BY o.position",
                Long.class, pollId, userId);
    }

    /** 이 글들 중 투표가 붙은 글(목록 줄의 {@code hasPoll}) */
    public Set<Long> postIdsWithPoll(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        String marks = String.join(", ", Collections.nCopies(postIds.size(), "?"));
        return new HashSet<>(jdbc.queryForList("SELECT post_id FROM polls WHERE post_id IN (" + marks + ")", Long.class, postIds.toArray()));
    }

    /** 투표를 만든다(선택지는 {@link #insertOptions}) */
    public void insert(long pollId, long postId, String question, boolean multiple, boolean changeAllowed, Instant closesAt, Instant now) {
        jdbc.update("INSERT INTO polls (id, post_id, question, multiple, change_allowed, closes_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                pollId, postId, question, multiple, changeAllowed, ts(closesAt), ts(now), ts(now));
    }

    /** 설정을 바꾼다(첫 표 전에만 — 부르는 쪽이 {@link #findForUpdate} 아래에서 확인한다) */
    public void update(long pollId, String question, boolean multiple, boolean changeAllowed, Instant closesAt, Instant now) {
        jdbc.update("UPDATE polls SET question = ?, multiple = ?, change_allowed = ?, closes_at = ?, updated_at = ? WHERE id = ?",
                question, multiple, changeAllowed, ts(closesAt), ts(now), pollId);
    }

    /** 선택지를 차례대로 넣는다 — {@code ids} 와 {@code labels} 는 같은 길이 */
    public void insertOptions(long pollId, List<Long> ids, List<String> labels) {
        for (int i = 0; i < labels.size(); i++) {
            jdbc.update("INSERT INTO poll_options (id, poll_id, position, label) VALUES (?, ?, ?, ?)", ids.get(i), pollId, i, labels.get(i));
        }
    }

    /** 선택지를 모두 지운다(고치기 — 첫 표 전이라 지워질 표가 없다) */
    public void deleteOptions(long pollId) {
        jdbc.update("DELETE FROM poll_options WHERE poll_id = ?", pollId);
    }

    /** 투표를 지운다 — 선택지·표가 함께 사라진다(ON DELETE CASCADE) */
    public void delete(long pollId) {
        jdbc.update("DELETE FROM polls WHERE id = ?", pollId);
    }

    /** 한 사람의 이 투표 요청을 줄 세운다(트랜잭션 끝까지) — 같은 사람이 동시에 다른 선택을 보내도 한쪽 선택만 남는다 */
    public void lockVoter(long pollId, long userId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(7340102, hashtext(?))::text", String.class, "poll:" + pollId + ":" + userId);
    }

    /** 그 사람의 표를 이 선택으로 바꾼다(처음이면 넣기만) — {@link #lockVoter} 아래에서 */
    public void replaceVotes(long pollId, long userId, Collection<Long> optionIds, Instant now) {
        jdbc.update("DELETE FROM poll_votes WHERE poll_id = ? AND user_id = ?", pollId, userId);
        for (Long optionId : optionIds) {
            jdbc.update("INSERT INTO poll_votes (poll_id, user_id, option_id, created_at) VALUES (?, ?, ?, ?)", pollId, userId, optionId, ts(now));
        }
    }

    private static PollData poll(ResultSet rs, int row) throws SQLException {
        return new PollData(rs.getLong("id"), rs.getLong("post_id"), rs.getString("question"), rs.getBoolean("multiple"), rs.getBoolean("change_allowed"),
                instant(rs.getTimestamp("closes_at")), instant(rs.getTimestamp("created_at")));
    }

    private static Optional<PollData> one(List<PollData> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
