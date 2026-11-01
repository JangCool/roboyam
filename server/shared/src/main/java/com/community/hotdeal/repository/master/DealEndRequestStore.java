package com.community.hotdeal.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 종료 요청 줄({@code deal_end_requests}, V122) — 글·회원마다 한 줄. <b>부르는 쪽 트랜잭션에 참여한다</b>(JdbcTemplate — 같은 연결). 요청자 신원은 이 표 밖으로 내지 않는다.
 *
 * <p><b>의심 구간</b>({@code flagged_period}): 지금 회차(마지막 「아직 진행 중」 뒤 — 운영진 재개는 줄을 지운다)의 요청이 문턱에 닿아 의심이 되면 그 회차의 줄을 모두 켜고,
 * 의심인 동안 들어온 요청도 켠 채로 넣는다. 「아직 진행 중」은 모두 끈다(작성자가 반박한 회차). 보상(규칙 5)은 딜이 끝날 때 켜진 줄의 요청자에게만 간다.
 */
@Repository
public class DealEndRequestStore {

    private final JdbcTemplate jdbc;

    public DealEndRequestStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 보상 대상 한 줄 — 요청 ID(원장 대상) · 요청한 회원 */
    public record Requester(long requestId, long memberId) {
    }

    public boolean exists(long postId, long memberId) {
        return !jdbc.queryForList("SELECT 1 FROM deal_end_requests WHERE post_id = ? AND member_id = ?", Integer.class, postId, memberId).isEmpty();
    }

    public void insert(long id, long postId, long memberId, Long authorId, String reason, boolean flaggedPeriod, boolean fromReport, Instant now) {
        jdbc.update("INSERT INTO deal_end_requests (id, post_id, member_id, author_id, reason, flagged_period, from_report, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, postId, memberId, authorId, reason, flaggedPeriod, fromReport, ts(now));
    }

    /** 그 회원이 {@code since} 뒤에 보낸 요청 수(하루 한도 — 한국 0시부터) */
    public int countByMemberSince(long memberId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE member_id = ? AND created_at >= ?", Integer.class, memberId, ts(since));
        return n == null ? 0 : n;
    }

    /** 그 회원이 {@code since} 뒤에 같은 작성자의 글에 보낸 요청 수(같은 작성자 기간 한도) */
    public int countByMemberAndAuthorSince(long memberId, long authorId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE member_id = ? AND author_id = ? AND created_at >= ?", Integer.class, memberId,
                authorId, ts(since));
        return n == null ? 0 : n;
    }

    /** 지금 회차의 요청 수 — {@code keptAt}(마지막 「아직 진행 중」) 뒤의 줄, 없으면 모든 줄 */
    public int countRound(long postId, Instant keptAt) {
        Integer n = keptAt == null ? jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, postId)
                : jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND created_at > ?", Integer.class, postId, ts(keptAt));
        return n == null ? 0 : n;
    }

    /** 지금 회차의 줄을 의심 구간으로 */
    public void markRoundFlagged(long postId, Instant keptAt) {
        if (keptAt == null) {
            jdbc.update("UPDATE deal_end_requests SET flagged_period = true WHERE post_id = ?", postId);
        } else {
            jdbc.update("UPDATE deal_end_requests SET flagged_period = true WHERE post_id = ? AND created_at > ?", postId, ts(keptAt));
        }
    }

    /** 「아직 진행 중」 — 그 글의 의심 구간 표시를 모두 끈다 */
    public void clearFlagged(long postId) {
        jdbc.update("UPDATE deal_end_requests SET flagged_period = false WHERE post_id = ? AND flagged_period", postId);
    }

    /** 의심 구간 요청자(보상 대상) — 요청 차례 */
    public List<Requester> flaggedRequesters(long postId) {
        return jdbc.query("SELECT id, member_id FROM deal_end_requests WHERE post_id = ? AND flagged_period ORDER BY created_at, id",
                (rs, i) -> new Requester(rs.getLong(1), rs.getLong(2)), postId);
    }

    /** 운영진 재개 — 그 글의 요청 줄을 모두 지운다(의심·요청 리셋) */
    public int deleteForPost(long postId) {
        return jdbc.update("DELETE FROM deal_end_requests WHERE post_id = ?", postId);
    }

    /** 탈퇴 — 그 회원의 요청 줄을 지운다(이미 의심이 된 딜의 상태는 그대로 — 규칙 6) */
    public int deleteForMember(long memberId) {
        return jdbc.update("DELETE FROM deal_end_requests WHERE member_id = ?", memberId);
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }
}
