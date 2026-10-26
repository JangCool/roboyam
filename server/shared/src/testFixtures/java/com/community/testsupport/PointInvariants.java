package com.community.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 포인트 불변식(docs/domain/commerce.md 5-5) 검사 — api·admin 의 HTTP 시험이 시험 데이터를 만든 뒤 부른다(shared 시험의 {@code PointTestKit.assertInvariants} 와 같은 검사를
 * 시험 문맥 밖에서도 쓰려고 단언 라이브러리 없이 <b>어긋난 것의 설명 목록</b>을 돌려준다 — 비어 있으면 성립).
 *
 * <p>회원마다 종류별로: 원장 몫 합 = 잔액 = 남은 묶음 합(합계 칸도 같다), 원장 줄마다 묶음 반영 합 = delta(무상·유상 몫까지, {@code lot_tracking_started_at} 이후 줄), 묶음마다
 * 남은 양 = 반영 합(전환 묶음은 + 원래 양), {@code balance_after} = 그때까지의 원장 합, 마지막 줄의 잔액 = 잔액, 미회수 ≥ 0.
 */
public final class PointInvariants {

    private PointInvariants() {
    }

    public static List<String> violations(JdbcTemplate jdbc, long... userIds) {
        List<String> out = new ArrayList<>();
        for (long userId : userIds) {
            String who = "회원 " + userId + ": ";
            List<long[]> rows = jdbc.query("SELECT free_balance, paid_balance, revoke_debt, balance FROM point_balances WHERE user_id = ?",
                    (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, userId);
            long[] b = rows.isEmpty() ? new long[] {0, 0, 0, 0} : rows.get(0);
            Map<String, Object> ledger = jdbc.queryForMap("SELECT coalesce(sum(free_delta), 0) AS f, coalesce(sum(paid_delta), 0) AS p, coalesce(sum(delta), 0) AS d "
                    + "FROM point_ledger WHERE user_id = ?", userId);
            Map<String, Object> lots = jdbc.queryForMap("SELECT coalesce(sum(remaining_amount) FILTER (WHERE kind = 'FREE'), 0) AS f, "
                    + "coalesce(sum(remaining_amount) FILTER (WHERE kind = 'PAID'), 0) AS p FROM point_lots WHERE user_id = ?", userId);
            check(out, who + "원장 무상 합 = 무상 잔액", ((Number) ledger.get("f")).longValue(), b[0]);
            check(out, who + "원장 유상 합 = 유상 잔액", ((Number) ledger.get("p")).longValue(), b[1]);
            check(out, who + "무상 묶음 합 = 무상 잔액", ((Number) lots.get("f")).longValue(), b[0]);
            check(out, who + "유상 묶음 합 = 유상 잔액", ((Number) lots.get("p")).longValue(), b[1]);
            check(out, who + "원장 합 = 합계", ((Number) ledger.get("d")).longValue(), b[3]);
            check(out, who + "합계 칸 = 무상 + 유상", b[0] + b[1], b[3]);
            if (b[2] < 0) {
                out.add(who + "미회수 < 0");
            }
            List<Long> badLines = jdbc.queryForList("SELECT l.id FROM point_ledger l LEFT JOIN ("
                    + "  SELECT x.ledger_id, sum(x.amount) AS s, coalesce(sum(x.amount) FILTER (WHERE t.kind = 'FREE'), 0) AS f, "
                    + "         coalesce(sum(x.amount) FILTER (WHERE t.kind = 'PAID'), 0) AS p "
                    + "  FROM point_ledger_lots x JOIN point_lots t ON t.id = x.lot_id GROUP BY x.ledger_id) r ON r.ledger_id = l.id "
                    + "WHERE l.user_id = ? AND l.created_at >= (SELECT lot_tracking_started_at FROM point_settings WHERE id = 1) "
                    + "AND (coalesce(r.s, 0) <> l.delta OR coalesce(r.f, 0) <> l.free_delta OR coalesce(r.p, 0) <> l.paid_delta)", Long.class, userId);
            if (!badLines.isEmpty()) {
                out.add(who + "묶음 반영 합 ≠ delta 인 줄 " + badLines);
            }
            List<Long> badLots = jdbc.queryForList("SELECT t.id FROM point_lots t LEFT JOIN (SELECT lot_id, sum(amount) AS s FROM point_ledger_lots GROUP BY lot_id) r "
                    + "ON r.lot_id = t.id WHERE t.user_id = ? AND t.remaining_amount <> coalesce(r.s, 0) + CASE WHEN t.source_reason = 'MIGRATION' THEN t.original_amount ELSE 0 END",
                    Long.class, userId);
            if (!badLots.isEmpty()) {
                out.add(who + "남은 양 ≠ 반영 합인 묶음 " + badLots);
            }
            List<Long> badAfter = jdbc.queryForList("SELECT id FROM (SELECT id, balance_after, sum(delta) OVER (ORDER BY id) AS running FROM point_ledger WHERE user_id = ?) x "
                    + "WHERE balance_after <> running", Long.class, userId);
            if (!badAfter.isEmpty()) {
                out.add(who + "balance_after ≠ 그때까지의 원장 합인 줄 " + badAfter);
            }
            List<Long> last = jdbc.queryForList("SELECT balance_after FROM point_ledger WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, userId);
            if (!last.isEmpty()) {
                check(out, who + "마지막 줄의 잔액 = 잔액", last.get(0), b[3]);
            }
        }
        return out;
    }

    private static void check(List<String> out, String rule, long actual, long expected) {
        if (actual != expected) {
            out.add(rule + " — " + actual + " ≠ " + expected);
        }
    }
}
