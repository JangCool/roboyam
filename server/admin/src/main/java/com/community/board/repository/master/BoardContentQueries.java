package com.community.board.repository.master;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 게시판 관리가 읽는 글·댓글 집계(글 수·말머리 사용 수·삭제 가능 판정)와 게시판을 지울 때의 글 정리.
 *
 * <p>native SQL 인 이유: 글·댓글 표(V42 의 {@code posts} 월 파티션 · {@code comments} 해시 파티션)는 이번에 <b>만들기만</b> 했고 엔티티는 글쓰기 기능이 정한다 —
 * 그 전까지 관리자 쪽은 집계만 하므로 엔티티를 미리 두지 않는다. 트랜잭션은 부르는 서비스의 것을 따른다(같은 DataSource).
 */
@Repository
public class BoardContentQueries {

    private final JdbcTemplate jdbc;

    public BoardContentQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 게시판별 글 수 — 삭제되지 않은 게시된 글(숨김 포함, 임시저장 제외). 글이 없는 게시판은 빠진다 */
    public Map<Long, Long> postCounts() {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT board_id, count(*) FROM posts WHERE deleted_at IS NULL AND status = 'PUBLISHED' GROUP BY board_id",
                rs -> {
                    out.put(rs.getLong(1), rs.getLong(2));
                });
        return out;
    }

    public long postCount(long boardId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM posts WHERE board_id = ? AND deleted_at IS NULL AND status = 'PUBLISHED'", Long.class, boardId);
        return count == null ? 0 : count;
    }

    /** 삭제되지 않은 글(임시저장 포함)이 하나라도 있는가 — 있으면 게시판을 지울 수 없다 */
    public boolean hasLivePosts(long boardId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM posts WHERE board_id = ? AND deleted_at IS NULL)", Boolean.class, boardId));
    }

    /** 이 게시판의 말머리별 사용 수(삭제·임시저장 포함) */
    public Map<Long, Long> prefixUsage(long boardId) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT prefix_id, count(*) FROM posts WHERE board_id = ? AND prefix_id IS NOT NULL GROUP BY prefix_id",
                rs -> {
                    out.put(rs.getLong(1), rs.getLong(2));
                }, boardId);
        return out;
    }

    /** 게시판을 지울 때 남은(삭제된) 글·댓글을 함께 지운다 — 파티션 표라 외래 키 연쇄 삭제가 없다 */
    public void deleteContent(long boardId) {
        jdbc.update("DELETE FROM comments WHERE board_id = ?", boardId);
        jdbc.update("DELETE FROM posts WHERE board_id = ?", boardId);
    }
}
