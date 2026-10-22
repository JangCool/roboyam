package com.community.board.repository.master;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 게시판 관리가 읽는 글·댓글 집계(글 수·말머리 사용 수·삭제 가능 판정)와 게시판을 지울 때의 글 정리.
 *
 * <p>native SQL 인 이유: 관리자 쪽은 글·댓글({@code posts} 월 파티션 · {@code comments} 해시 파티션)과 추천·조회 기록 표를 게시판 단위로 세거나 한꺼번에 지우기만 한다 —
 * 엔티티를 올리지 않고 집계·일괄 삭제 문장으로 끝낸다. 트랜잭션은 부르는 서비스의 것을 따른다(같은 DataSource).
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

    /**
     * 게시판을 지울 때 남은(삭제된) 글·댓글과 그 글에 딸린 추천·조회 기록을 함께 지운다 — 파티션 표라 외래 키 연쇄 삭제가 없다. 글을 지우기 전에 딸린 행부터 지운다
     * (글 ID 를 이 게시판의 글에서 고른다 — {@code ix_posts_board_prefix} 의 {@code board_id} 앞부분을 탄다).
     * <ul>
     *   <li>{@code post_likes}·{@code post_views}: 글 ID 해시 조각 — {@code post_id} 가 PK 앞부분이라 조각마다 PK 색인으로 찾는다.</li>
     *   <li>{@code comment_likes}: 댓글 ID 해시 조각이지만 {@code post_id} 칸과 {@code ix_comment_likes_post_user (post_id, user_id)} 가 있어 글 ID 로 찾는다.</li>
     *   <li><b>신고({@code reports})는 지우지 않는다</b> — 신고자·당시 내용 스냅샷은 증거라 게시판이 지워져도 남기고, 개인정보 보유기간 배치(신고 기록 축 {@code R__batch_18})가
     *       기간이 지나면 지운다.</li>
     * </ul>
     */
    public void deleteContent(long boardId) {
        jdbc.update("DELETE FROM post_likes WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", boardId);
        jdbc.update("DELETE FROM comment_likes WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", boardId);
        jdbc.update("DELETE FROM post_views WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", boardId);
        jdbc.update("DELETE FROM comments WHERE board_id = ?", boardId);
        jdbc.update("DELETE FROM posts WHERE board_id = ?", boardId);
    }
}
