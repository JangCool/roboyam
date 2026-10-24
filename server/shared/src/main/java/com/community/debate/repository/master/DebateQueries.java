package com.community.debate.repository.master;

import com.community.debate.entity.DebateStance;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 토론의 작은 표 둘(V76) — 회원의 글별 현재 입장({@code post_stances})과 운영자가 고정한 의견({@code debate_pins}). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>Native SQL 인 이유: 처음 입장을 정하는 쓰기가 동시에 둘 들어와도 한 입장만 남아야 해서 {@code INSERT … ON CONFLICT DO NOTHING} 을 쓴다
 * (실패한 문장이 트랜잭션을 망가뜨리지 않게). 고정도 같은 방식(멱등). 두 표 모두 행 하나에 칸 몇 개라 엔티티를 두지 않는다.
 */
@Repository
public class DebateQueries {

    private final JdbcTemplate jdbc;

    public DebateQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 그 회원의 그 글 현재 입장 */
    public Optional<DebateStance> stanceOf(long postId, long userId) {
        List<String> found = jdbc.queryForList("SELECT stance FROM post_stances WHERE post_id = ? AND user_id = ?", String.class, postId, userId);
        return found.isEmpty() ? Optional.empty() : Optional.of(DebateStance.valueOf(found.get(0)));
    }

    /** 처음 입장을 정한다 — 이미 있으면 그대로 두고, <b>정해진(저장된) 입장</b>을 돌려준다(동시에 다른 입장이 먼저 정해졌으면 그 입장) */
    public DebateStance settle(long postId, long userId, DebateStance stance, Instant now) {
        jdbc.update("INSERT INTO post_stances (post_id, user_id, stance, created_at, updated_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                postId, userId, stance.name(), Timestamp.from(now), Timestamp.from(now));
        String stored = jdbc.queryForObject("SELECT stance FROM post_stances WHERE post_id = ? AND user_id = ?", String.class, postId, userId);
        return DebateStance.valueOf(stored);
    }

    /** 입장을 바꾼다 → 바꾼 줄 수(입장이 없으면 0) */
    public int change(long postId, long userId, DebateStance stance, Instant now) {
        return jdbc.update("UPDATE post_stances SET stance = ?, updated_at = ? WHERE post_id = ? AND user_id = ?", stance.name(), Timestamp.from(now), postId,
                userId);
    }

    /**
     * 입장 바꾸기의 소급 — 그 회원이 그 글에 쓴 <b>입장 있는 댓글</b>(원댓글·답글, 숨긴 것·지운 것 포함)의 배지를 새 입장으로 바꾼다. 일반 댓글(배지 없음)은 그대로.
     * 지운 댓글도 함께 바꿔 답글이 달린 지운 자리가 옛 입장 탭에 남지 않게 한다. 고정({@code debate_pins})·숨김 칸은 건드리지 않는다.
     *
     * @return 바뀐 의견 중 <b>지우지 않은 것</b>의 수(공개 목록 {@code debate.myOpinionCount} 와 같은 기준)
     */
    public int moveOpinions(long postId, long userId, DebateStance stance) {
        Integer moved = jdbc.queryForObject("""
                WITH moved AS (
                    UPDATE comments SET stance = ?
                     WHERE post_id = ? AND author_id = ? AND stance IS NOT NULL AND stance <> ?
                    RETURNING deleted_at
                )
                SELECT count(*) FILTER (WHERE deleted_at IS NULL) FROM moved
                """, Integer.class, stance.name(), postId, userId, stance.name());
        return moved == null ? 0 : moved;
    }

    /** 그 글에서 고정한 의견(댓글 ID) */
    public Set<Long> pinnedOf(long postId) {
        return new HashSet<>(jdbc.queryForList("SELECT comment_id FROM debate_pins WHERE post_id = ?", Long.class, postId));
    }

    /** 고정 — 이미 고정이면 그대로 */
    public void pin(long commentId, long postId, long operatorId, Instant now) {
        jdbc.update("INSERT INTO debate_pins (comment_id, post_id, pinned_by, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING", commentId, postId,
                operatorId, Timestamp.from(now));
    }

    /** 고정 해제 — 고정이 아니면 그대로 */
    public void unpin(long commentId) {
        jdbc.update("DELETE FROM debate_pins WHERE comment_id = ?", commentId);
    }
}
