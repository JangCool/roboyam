package com.community.sanction.repository.master;

import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.BoardSanctionStore.ReviewRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 게시판 제재·반복 검토 목록·건수·회원 요약(읽기만) — 한 줄 모양은 shared {@link BoardSanctionStore}({@code SELECT}·{@code row}·{@code REVIEW_SELECT}·{@code review}).
 * 색인: 목록은 V137 의 {@code ix_board_sanctions_created}·{@code ix_board_sanctions_running_list}·{@code ix_board_sanctions_closed_list}·{@code ix_board_sanctions_board},
 * 반복 검토는 {@code ix_board_sanction_reviews_open_list}·{@code ix_board_sanction_reviews_closed_list}, 회원 범위는 {@code ix_board_sanctions_user}.
 */
@Repository
public class AdminBoardSanctionStore {

    /** 범위 「진행 중」 — 진행 중 쓰기 제한만(결정 11, 끝이 지난 것은 뺀다). 지금 자리 하나 */
    static final String RUNNING = BoardSanctionStore.RUNNING;
    /** 범위 「끝남」 — 기간 끝·해제·무효 */
    static final String CLOSED = "s.status <> 'ACTIVE'";

    /** 목록 거르기 */
    public record Filter(String scope, Long boardId, BoardSanctionKind kind, String nicknameLike, Long sanctionNo, Instant from, Instant to, Long userId, boolean oldest) {
    }

    /** 게시판 거르기 고르개 한 줄 */
    public record BoardRef(long id, String slug, String name, String nameEn) {
    }

    /** 반복 검토 줄 + 지금(열린 건) 또는 닫은 때(닫힌 건)의 7일 건수 · 게시판 수 */
    public record ReviewLine(ReviewRow row, int recentCount, int recentBoardCount) {
    }

    /** 회원 상세의 게시판 하나 — 유효한 경고 수 · 유효한 쓰기 제한 수 · 진행 중 쓰기 제한의 끝 */
    public record MemberBoardRow(BoardRef board, long warnings, long restrictions, Instant activeUntil) {
    }

    private final JdbcTemplate jdbc;

    public AdminBoardSanctionStore(BoardSanctionStore store) {
        this.jdbc = store.jdbc();
    }

    // ── 제재 목록 ─────────────────────────────────────────────────────────

    /** 전체 건수(거르기와 무관) — all · active · closed */
    public long[] counts(Instant now) {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM board_sanctions), (SELECT count(*) FROM board_sanctions s WHERE " + RUNNING + "), "
                + "(SELECT count(*) FROM board_sanctions s WHERE " + CLOSED + ")", (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, ts(now));
    }

    public long total(Filter filter, Instant now) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, now, args);
        Long n = jdbc.queryForObject("SELECT count(*) FROM board_sanctions s" + (filter.nicknameLike() != null ? " LEFT JOIN profiles p ON p.user_id = s.user_id" : "")
                + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<BoardSanctionRow> page(Filter filter, Instant now, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, now, args);
        String order = filter.oldest() ? " ORDER BY s.created_at, s.id" : " ORDER BY s.created_at DESC, s.id DESC";
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(BoardSanctionStore.SELECT + where + order + " LIMIT ? OFFSET ?", BoardSanctionStore::row, args.toArray());
    }

    private static String where(Filter f, Instant now, List<Object> args) {
        List<String> parts = new ArrayList<>();
        if ("ACTIVE".equals(f.scope())) {
            parts.add(RUNNING);
            args.add(ts(now));
        } else if ("CLOSED".equals(f.scope())) {
            parts.add(CLOSED);
        }
        if (f.boardId() != null) {
            parts.add("s.board_id = ?");
            args.add(f.boardId());
        }
        if (f.kind() != null) {
            parts.add("s.kind = ?");
            args.add(f.kind().name());
        }
        if (f.nicknameLike() != null) {
            parts.add("lower(p.nickname) LIKE ? ESCAPE '\\'");
            args.add(f.nicknameLike());
        }
        if (f.sanctionNo() != null) {
            parts.add("s.sanction_no = ?");
            args.add(f.sanctionNo());
        }
        if (f.from() != null) {
            parts.add("s.created_at >= ?");
            args.add(ts(f.from()));
        }
        if (f.to() != null) {
            parts.add("s.created_at <= ?");
            args.add(ts(f.to()));
        }
        if (f.userId() != null) {
            parts.add("s.user_id = ?");
            args.add(f.userId());
        }
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    /** 게시판 제재가 한 건이라도 있는 게시판(이름 차례) — 게시판 거르기 고르개 */
    public List<BoardRef> boards() {
        return jdbc.query("SELECT b.id, b.slug, b.name, b.name_en FROM boards b WHERE EXISTS (SELECT 1 FROM board_sanctions s WHERE s.board_id = b.id) ORDER BY b.name, b.id",
                (rs, i) -> new BoardRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    // ── 회원 상세 ─────────────────────────────────────────────────────────

    /** 그 회원의 게시판별 수 — 게시판 이름 차례 */
    public List<MemberBoardRow> memberBoards(long userId, Instant now) {
        return jdbc.query("SELECT s.board_id, b.slug, b.name, b.name_en, count(*) FILTER (WHERE s.status <> 'VOIDED' AND s.kind = 'WARNING'), "
                        + "count(*) FILTER (WHERE s.status <> 'VOIDED' AND s.kind <> 'WARNING'), max(s.ends_at) FILTER (WHERE " + RUNNING + ") "
                        + "FROM board_sanctions s JOIN boards b ON b.id = s.board_id WHERE s.user_id = ? GROUP BY s.board_id, b.slug, b.name, b.name_en ORDER BY b.name, s.board_id",
                (rs, i) -> new MemberBoardRow(new BoardRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), rs.getLong(5), rs.getLong(6),
                        MemberSanctionStore.instant(rs, 7)), ts(now), userId);
    }

    // ── 반복 검토 ─────────────────────────────────────────────────────────

    /** 열린 건 · 닫힌 건 수 */
    public long[] reviewCounts() {
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE status = 'OPEN'), count(*) FILTER (WHERE status = 'CLOSED') FROM board_sanction_reviews",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)});
    }

    /** 열린 건 수(탭·메뉴 배지) — 부분 색인 */
    public long openReviews() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM board_sanction_reviews WHERE status = 'OPEN'", Long.class);
        return n == null ? 0 : n;
    }

    /** 한 쪽 — 열린 건 오래된 것이 위, 닫힌 건 최근 닫은 것이 위. 건수는 줄마다 회원·게시판 범위 색인 COUNT */
    public List<ReviewLine> reviewPage(boolean open, Instant now, int page, int size) {
        Instant from = now.minus(BoardSanctionStore.REVIEW_WINDOW);
        List<ReviewRow> rows = jdbc.query(BoardSanctionStore.REVIEW_SELECT + " WHERE r.status = ?" + (open ? " ORDER BY r.opened_at, r.id" : " ORDER BY r.closed_at DESC, r.id DESC")
                + " LIMIT ? OFFSET ?", BoardSanctionStore::review, open ? "OPEN" : "CLOSED", size, (long) page * size);
        List<ReviewLine> out = new ArrayList<>(rows.size());
        for (ReviewRow r : rows) {
            out.add(line(r, from));
        }
        return out;
    }

    /** 검토 한 줄의 건수 — 열린 건은 지금 기준(7일 · VOIDED 제외 · 마지막 닫힌 검토 이후), 닫힌 건은 닫은 때 저장한 수 */
    public ReviewLine line(ReviewRow r, Instant windowFrom) {
        if (!r.open()) {
            return new ReviewLine(r, r.closedCount() == null ? r.openedCount() : r.closedCount(), r.closedBoardCount() == null ? 1 : r.closedBoardCount());
        }
        Instant from = r.countedAfter() != null && r.countedAfter().isAfter(windowFrom) ? r.countedAfter() : windowFrom;
        Integer count = jdbc.queryForObject("SELECT count(*) FROM board_sanctions WHERE user_id = ? AND board_id = ? AND status <> 'VOIDED' AND created_at > ?",
                Integer.class, r.userId(), r.boardId(), ts(from));
        Integer boards = jdbc.queryForObject("SELECT count(DISTINCT board_id) FROM board_sanctions WHERE user_id = ? AND status <> 'VOIDED' AND created_at > ?",
                Integer.class, r.userId(), ts(windowFrom));
        return new ReviewLine(r, count == null ? 0 : count, boards == null ? 0 : boards);
    }

    private static Object ts(Instant at) {
        return MemberSanctionStore.ts(at);
    }
}
