package com.community.groupbuy.service;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 공구 불변식 검사(commerce.md 18절 「불변식 추가」, 계약 결정 28·39·65·77·80) — <b>한 SQL</b>(같은 스냅샷)로 어긋난 줄만 돌려준다(읽기만). 비면 맞다.
 *
 * <ol>
 *   <li>공구·옵션별 {@code current_qty} = 결제 확정된 주문 줄의 판매 수량 Σ(qty − canceled − restocked)(결제 중·만료 주문은 빼고)</li>
 *   <li>공구·옵션별 {@code reserved_qty} = 결제 중 주문 줄 수량 Σ</li>
 *   <li>옵션 있는 공구: Σ옵션 current = 공구 current, Σ옵션 reserved = 공구 reserved(옵션 최대 합과 공구 최대는 관계 없다)</li>
 *   <li>수량 이동 줄의 마지막(ID 순) 이후 값 = 지금 값(공구·옵션)</li>
 *   <li>모집을 시작한 공구: {@code max_qty = initial_max_qty + ΣADD − ΣREDUCE}, {@code added_qty = ΣADD}</li>
 *   <li>받아들인 결제(이상 없음): 결제 − 취소됨 − 아직 끝나지 않은 취소 = 현금 − 환불 누계 − 보상 누계(결정 39)</li>
 * </ol>
 * 대상 공구를 주면 그 공구들만 본다(시험·운영자 점검), 비면 전체.
 */
@Component
public class GroupBuyInvariantCheck {

    /** 어긋난 한 줄 — {@code check} 이름, 대상(공구·옵션·주문) ID, 기대값, 실제값 */
    public record Violation(String check, long targetId, long expected, long actual) {
    }

    private static final String SQL = """
            WITH g AS (SELECT * FROM group_buys WHERE (cardinality(?::bigint[]) = 0 OR id = ANY (?::bigint[]))),
            lines AS (
                SELECT i.group_buy_id, i.option_id,
                       CASE WHEN p.status NOT IN ('PENDING_PAYMENT', 'EXPIRED') THEN i.qty - i.canceled_qty - i.restocked_qty ELSE 0 END AS sold_qty,
                       CASE WHEN p.status = 'PENDING_PAYMENT' THEN i.qty ELSE 0 END AS held_qty
                FROM group_buy_participation_items i JOIN group_buy_participations p ON p.id = i.participation_id
                WHERE i.group_buy_id IN (SELECT id FROM g)),
            sold AS (SELECT group_buy_id, sum(sold_qty) AS current_qty, sum(held_qty) AS reserved_qty FROM lines GROUP BY group_buy_id),
            option_sold AS (SELECT option_id, sum(sold_qty) AS current_qty, sum(held_qty) AS reserved_qty FROM lines WHERE option_id IS NOT NULL GROUP BY option_id),
            last_move AS (SELECT DISTINCT ON (group_buy_id) group_buy_id, current_after, reserved_after, max_after FROM group_buy_qty_movements
                          WHERE group_buy_id IN (SELECT id FROM g) ORDER BY group_buy_id, id DESC),
            last_option_move AS (SELECT DISTINCT ON (option_id) option_id, option_current_after, option_reserved_after, option_max_after FROM group_buy_qty_movements
                                 WHERE group_buy_id IN (SELECT id FROM g) AND option_id IS NOT NULL AND option_current_after IS NOT NULL ORDER BY option_id, id DESC),
            adjust AS (SELECT group_buy_id, coalesce(sum(qty) FILTER (WHERE kind = 'ADD'), 0) AS added, coalesce(sum(qty) FILTER (WHERE kind = 'REDUCE'), 0) AS reduced
                       FROM group_buy_qty_movements WHERE group_buy_id IN (SELECT id FROM g) GROUP BY group_buy_id),
            opt_sum AS (SELECT group_buy_id, sum(current_qty) AS current_qty, sum(reserved_qty) AS reserved_qty FROM group_buy_options
                        WHERE group_buy_id IN (SELECT id FROM g) GROUP BY group_buy_id),
            pay AS (SELECT p.id, p.cash_amount - p.refunded_amount - p.compensation_amount AS expected,
                           po.paid_amount - po.canceled_amount - coalesce((SELECT sum(c.amount) FROM payment_cancels c WHERE c.payment_order_id = po.id
                               AND c.status IN ('PENDING', 'SENDING', 'GAVE_UP')), 0) AS actual
                    FROM group_buy_participations p JOIN payment_orders po ON po.order_type = 'GROUP_BUY' AND po.order_ref_id = p.id
                        AND po.anomaly IS NULL AND po.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED')
                    WHERE p.group_buy_id IN (SELECT id FROM g))
            SELECT 'GROUP_CURRENT', g.id, coalesce(s.current_qty, 0), g.current_qty FROM g LEFT JOIN sold s ON s.group_buy_id = g.id
                WHERE g.current_qty <> coalesce(s.current_qty, 0)
            UNION ALL
            SELECT 'GROUP_RESERVED', g.id, coalesce(s.reserved_qty, 0), g.reserved_qty FROM g LEFT JOIN sold s ON s.group_buy_id = g.id
                WHERE g.reserved_qty <> coalesce(s.reserved_qty, 0)
            UNION ALL
            SELECT 'OPTION_CURRENT', o.id, coalesce(s.current_qty, 0), o.current_qty FROM group_buy_options o LEFT JOIN option_sold s ON s.option_id = o.id
                WHERE o.group_buy_id IN (SELECT id FROM g) AND o.current_qty <> coalesce(s.current_qty, 0)
            UNION ALL
            SELECT 'OPTION_RESERVED', o.id, coalesce(s.reserved_qty, 0), o.reserved_qty FROM group_buy_options o LEFT JOIN option_sold s ON s.option_id = o.id
                WHERE o.group_buy_id IN (SELECT id FROM g) AND o.reserved_qty <> coalesce(s.reserved_qty, 0)
            UNION ALL
            SELECT 'OPTIONS_SUM_CURRENT', g.id, g.current_qty, os.current_qty FROM g JOIN opt_sum os ON os.group_buy_id = g.id WHERE os.current_qty <> g.current_qty
            UNION ALL
            SELECT 'OPTIONS_SUM_RESERVED', g.id, g.reserved_qty, os.reserved_qty FROM g JOIN opt_sum os ON os.group_buy_id = g.id WHERE os.reserved_qty <> g.reserved_qty
            UNION ALL
            SELECT 'LAST_MOVE_GROUP', g.id, g.current_qty::bigint * 1000000000 + g.reserved_qty::bigint * 100000 + g.max_qty,
                   lm.current_after::bigint * 1000000000 + lm.reserved_after::bigint * 100000 + lm.max_after FROM g JOIN last_move lm ON lm.group_buy_id = g.id
                WHERE (lm.current_after, lm.reserved_after, lm.max_after) <> (g.current_qty, g.reserved_qty, g.max_qty)
            UNION ALL
            SELECT 'LAST_MOVE_OPTION', o.id, o.current_qty::bigint * 1000000 + o.reserved_qty, lm.option_current_after::bigint * 1000000 + lm.option_reserved_after
                FROM group_buy_options o JOIN last_option_move lm ON lm.option_id = o.id
                WHERE (lm.option_current_after, lm.option_reserved_after) <> (o.current_qty, o.reserved_qty) OR lm.option_max_after IS DISTINCT FROM o.max_qty
            UNION ALL
            SELECT 'MAX_FORMULA', g.id, g.initial_max_qty + coalesce(a.added, 0) - coalesce(a.reduced, 0), g.max_qty FROM g LEFT JOIN adjust a ON a.group_buy_id = g.id
                WHERE g.initial_max_qty IS NOT NULL AND g.max_qty <> g.initial_max_qty + coalesce(a.added, 0) - coalesce(a.reduced, 0)
            UNION ALL
            SELECT 'ADDED_QTY', g.id, coalesce(a.added, 0), g.added_qty FROM g LEFT JOIN adjust a ON a.group_buy_id = g.id
                WHERE g.initial_max_qty IS NOT NULL AND g.added_qty <> coalesce(a.added, 0)
            UNION ALL
            SELECT 'PAYMENT_BALANCE', pay.id, pay.expected, pay.actual FROM pay WHERE pay.expected <> pay.actual
            """;

    private final JdbcTemplate jdbc;

    public GroupBuyInvariantCheck(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 어긋난 줄(비면 맞다) */
    public List<Violation> violations(List<Long> groupBuyIds) {
        Long[] ids = groupBuyIds == null ? new Long[0] : groupBuyIds.toArray(Long[]::new);
        return jdbc.query(SQL, (rs, i) -> new Violation(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)), ids, ids);
    }
}
