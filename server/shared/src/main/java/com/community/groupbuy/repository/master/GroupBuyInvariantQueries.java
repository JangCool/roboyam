package com.community.groupbuy.repository.master;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공구 불변식 검사의 SQL({@code GroupBuyInvariantCheck} — 무엇을 보는지는 그 설명) — <b>한 문장</b>(같은 스냅샷)으로 어긋난 줄만 돌려준다(읽기만).
 *
 * <p>Native SQL 인 이유: 여러 표(공구·단품·주문 줄·수량 이동·결제·취소·요청)를 같은 스냅샷에서 맞대는 CTE 집계이고 {@code DISTINCT ON}(마지막 수량 이동)·{@code FILTER}·{@code UNION ALL}
 * 이 핵심이다 — 문장을 나누면 사이에 바뀐 값 때문에 맞는 데이터도 어긋나 보인다.
 */
@Repository
public class GroupBuyInvariantQueries {

    /** 어긋난 한 줄 — 검사 이름, 대상(공구·단품·주문·요청) ID, 기대값, 실제값 */
    public record ViolationRow(String check, long targetId, long expected, long actual) {
    }

    private static final String SQL = """
            WITH g AS (SELECT * FROM group_buys WHERE (cardinality(?::bigint[]) = 0 OR id = ANY (?::bigint[]))),
            lines AS (
                SELECT i.group_buy_id, i.sku_id, s.kind,
                       CASE WHEN p.status NOT IN ('PENDING_PAYMENT', 'EXPIRED') THEN i.qty - i.canceled_qty - i.restocked_qty ELSE 0 END AS sold_qty,
                       CASE WHEN p.status = 'PENDING_PAYMENT' THEN i.qty ELSE 0 END AS held_qty
                FROM group_buy_participation_items i JOIN group_buy_participations p ON p.id = i.participation_id
                    JOIN group_buy_skus s ON s.id = i.sku_id
                WHERE i.group_buy_id IN (SELECT id FROM g)),
            sold AS (SELECT group_buy_id, sum(sold_qty) AS current_qty, sum(held_qty) AS reserved_qty FROM lines WHERE kind = 'MAIN' GROUP BY group_buy_id),
            sku_sold AS (SELECT sku_id, sum(sold_qty) AS current_qty, sum(held_qty) AS reserved_qty FROM lines GROUP BY sku_id),
            last_move AS (SELECT DISTINCT ON (group_buy_id) group_buy_id, current_after, reserved_after, max_after FROM group_buy_qty_movements
                          WHERE group_buy_id IN (SELECT id FROM g) AND current_after IS NOT NULL ORDER BY group_buy_id, id DESC),
            last_sku_move AS (SELECT DISTINCT ON (sku_id) sku_id, sku_current_after, sku_reserved_after, sku_max_after FROM group_buy_qty_movements
                              WHERE group_buy_id IN (SELECT id FROM g) AND sku_id IS NOT NULL AND sku_current_after IS NOT NULL ORDER BY sku_id, id DESC),
            adjust AS (SELECT group_buy_id, coalesce(sum(qty) FILTER (WHERE kind = 'ADD'), 0) AS added, coalesce(sum(qty) FILTER (WHERE kind = 'REDUCE'), 0) AS reduced
                       FROM group_buy_qty_movements WHERE group_buy_id IN (SELECT id FROM g) AND max_after IS NOT NULL GROUP BY group_buy_id),
            main_sum AS (SELECT group_buy_id, sum(current_qty) AS current_qty, sum(reserved_qty) AS reserved_qty FROM group_buy_skus
                         WHERE group_buy_id IN (SELECT id FROM g) AND kind = 'MAIN' GROUP BY group_buy_id),
            pay AS (SELECT p.id, p.cash_amount - p.refunded_amount - p.compensation_amount AS expected,
                           po.paid_amount - po.canceled_amount - coalesce((SELECT sum(c.amount) FROM payment_cancels c WHERE c.payment_order_id = po.id
                               AND (c.status IN ('PENDING', 'SENDING', 'GAVE_UP') OR (c.status = 'MANUAL' AND c.manual_method = 'BANK_TRANSFER'))), 0) AS actual
                    FROM group_buy_participations p JOIN payment_orders po ON po.order_type = 'GROUP_BUY' AND po.order_ref_id = p.id
                        AND po.anomaly IS NULL AND po.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED')
                    WHERE p.group_buy_id IN (SELECT id FROM g)),
            fee AS (SELECT r.id, r.fee_paid_amount - r.fee_refunded_amount AS expected,
                           coalesce((SELECT sum(po.paid_amount - po.canceled_amount - coalesce((SELECT sum(c.amount) FROM payment_cancels c WHERE c.payment_order_id = po.id
                               AND (c.status IN ('PENDING', 'SENDING', 'GAVE_UP') OR (c.status = 'MANUAL' AND c.manual_method = 'BANK_TRANSFER'))), 0))
                               FROM payment_orders po WHERE po.order_type = 'GROUP_BUY_RETURN_FEE' AND po.order_ref_id = r.id AND po.anomaly IS NULL
                               AND po.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED')), 0) AS actual
                    FROM group_buy_requests r WHERE r.group_buy_id IN (SELECT id FROM g))
            SELECT 'GROUP_CURRENT', g.id, coalesce(s.current_qty, 0), g.current_qty FROM g LEFT JOIN sold s ON s.group_buy_id = g.id
                WHERE g.current_qty <> coalesce(s.current_qty, 0)
            UNION ALL
            SELECT 'GROUP_RESERVED', g.id, coalesce(s.reserved_qty, 0), g.reserved_qty FROM g LEFT JOIN sold s ON s.group_buy_id = g.id
                WHERE g.reserved_qty <> coalesce(s.reserved_qty, 0)
            UNION ALL
            SELECT 'SKU_CURRENT', k.id, coalesce(s.current_qty, 0), k.current_qty FROM group_buy_skus k LEFT JOIN sku_sold s ON s.sku_id = k.id
                WHERE k.group_buy_id IN (SELECT id FROM g) AND k.current_qty <> coalesce(s.current_qty, 0)
            UNION ALL
            SELECT 'SKU_RESERVED', k.id, coalesce(s.reserved_qty, 0), k.reserved_qty FROM group_buy_skus k LEFT JOIN sku_sold s ON s.sku_id = k.id
                WHERE k.group_buy_id IN (SELECT id FROM g) AND k.reserved_qty <> coalesce(s.reserved_qty, 0)
            UNION ALL
            SELECT 'MAIN_SUM_CURRENT', g.id, g.current_qty, ms.current_qty FROM g JOIN main_sum ms ON ms.group_buy_id = g.id WHERE ms.current_qty <> g.current_qty
            UNION ALL
            SELECT 'MAIN_SUM_RESERVED', g.id, g.reserved_qty, ms.reserved_qty FROM g JOIN main_sum ms ON ms.group_buy_id = g.id WHERE ms.reserved_qty <> g.reserved_qty
            UNION ALL
            SELECT 'LAST_MOVE_GROUP', g.id, g.current_qty::bigint * 1000000000 + g.reserved_qty::bigint * 100000 + g.max_qty,
                   lm.current_after::bigint * 1000000000 + lm.reserved_after::bigint * 100000 + lm.max_after FROM g JOIN last_move lm ON lm.group_buy_id = g.id
                WHERE (lm.current_after, lm.reserved_after, lm.max_after) <> (g.current_qty, g.reserved_qty, g.max_qty)
            UNION ALL
            SELECT 'LAST_MOVE_SKU', k.id, k.current_qty::bigint * 1000000 + k.reserved_qty, lm.sku_current_after::bigint * 1000000 + lm.sku_reserved_after
                FROM group_buy_skus k JOIN last_sku_move lm ON lm.sku_id = k.id
                WHERE (lm.sku_current_after, lm.sku_reserved_after) <> (k.current_qty, k.reserved_qty) OR lm.sku_max_after IS DISTINCT FROM k.max_qty
            UNION ALL
            SELECT 'MAX_FORMULA', g.id, g.initial_max_qty + coalesce(a.added, 0) - coalesce(a.reduced, 0), g.max_qty FROM g LEFT JOIN adjust a ON a.group_buy_id = g.id
                WHERE g.initial_max_qty IS NOT NULL AND g.max_qty <> g.initial_max_qty + coalesce(a.added, 0) - coalesce(a.reduced, 0)
            UNION ALL
            SELECT 'ADDED_QTY', g.id, coalesce(a.added, 0), g.added_qty FROM g LEFT JOIN adjust a ON a.group_buy_id = g.id
                WHERE g.initial_max_qty IS NOT NULL AND g.added_qty <> coalesce(a.added, 0)
            UNION ALL
            SELECT 'PAYMENT_BALANCE', pay.id, pay.expected, pay.actual FROM pay WHERE pay.expected <> pay.actual
            UNION ALL
            SELECT 'RETURN_FEE_BALANCE', fee.id, fee.expected, fee.actual FROM fee WHERE fee.expected <> fee.actual
            """;

    private final JdbcTemplate jdbc;

    public GroupBuyInvariantQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 어긋난 줄(비면 맞다) — 대상 공구 ID 가 비면 전체 */
    public List<ViolationRow> violations(Long[] groupBuyIds) {
        return jdbc.query(SQL, (rs, i) -> new ViolationRow(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)), groupBuyIds, groupBuyIds);
    }
}
