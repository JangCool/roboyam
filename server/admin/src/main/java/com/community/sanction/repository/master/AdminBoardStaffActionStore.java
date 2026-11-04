package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.MemberSanctionStore.ts;

import com.community.sanction.repository.master.AdminBoardSanctionStore.BoardRef;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 「스텝 처리 감독」 목록(읽기만, board-staff-action-contract 「API > 관리자」) — 한 줄 모양은 shared {@link BoardStaffActionStore}({@code SELECT}·{@code row}).
 * 제재 줄(kind SANCTION)은 넣지 않는다. 색인: V138 의 {@code ix_board_staff_actions_content}(전체) · {@code ix_board_staff_actions_board_content}(게시판).
 */
@Repository
public class AdminBoardStaffActionStore {

    /**
     * 목록 거르기.
     *
     * @param kind         POST_HIDE · POST_DELETE · COMMENT_DELETE(null 이면 전체)
     * @param restored     OPEN(되돌리지 않음) · RESTORED(되돌림) · null 전체
     * @param nicknameLike 처리자 닉네임 부분 일치(소문자, LIKE 특수 글자 처리 끝) — null 이면 거르지 않음
     */
    public record Filter(Long boardId, String kind, String restored, String nicknameLike, Instant from, Instant to) {
    }

    private final JdbcTemplate jdbc;

    public AdminBoardStaffActionStore(BoardStaffActionStore store) {
        this.jdbc = store.jdbc();
    }

    public long total(Filter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Long n = jdbc.queryForObject("SELECT count(*) FROM board_staff_actions a" + (filter.nicknameLike() != null ? " LEFT JOIN profiles ap ON ap.user_id = a.actor_id" : "")
                + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    /** 최신 것이 위, {@code page} 0부터 */
    public List<ActionRow> page(Filter filter, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(BoardStaffActionStore.SELECT + where + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?", BoardStaffActionStore::row, args.toArray());
    }

    /** 스텝 처리가 한 건이라도 있는 게시판(이름 차례) — 게시판 거르기 고르개 */
    public List<BoardRef> boards() {
        return jdbc.query("SELECT b.id, b.slug, b.name, b.name_en FROM boards b WHERE EXISTS (SELECT 1 FROM board_staff_actions a WHERE a.board_id = b.id AND "
                + BoardStaffActionStore.CONTENT + ") ORDER BY b.name, b.id", (rs, i) -> new BoardRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    private static String where(Filter f, List<Object> args) {
        List<String> parts = new ArrayList<>();
        parts.add(BoardStaffActionStore.CONTENT);
        if (f.boardId() != null) {
            parts.add("a.board_id = ?");
            args.add(f.boardId());
        }
        if (f.kind() != null) {
            int cut = f.kind().indexOf('_');
            parts.add("a.kind = ? AND a.action = ?");
            args.add(f.kind().substring(0, cut));
            args.add(f.kind().substring(cut + 1));
        }
        if ("OPEN".equals(f.restored())) {
            parts.add("a.restored_at IS NULL");
        } else if ("RESTORED".equals(f.restored())) {
            parts.add("a.restored_at IS NOT NULL");
        }
        if (f.nicknameLike() != null) {
            parts.add("lower(ap.nickname) LIKE ? ESCAPE '\'");
            args.add(f.nicknameLike());
        }
        if (f.from() != null) {
            parts.add("a.created_at >= ?");
            args.add(ts(f.from()));
        }
        if (f.to() != null) {
            parts.add("a.created_at <= ?");
            args.add(ts(f.to()));
        }
        return " WHERE " + String.join(" AND ", parts);
    }
}
